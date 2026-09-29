package dev.example.grpc.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import dev.example.grpc.hello.DemoTokens;
import dev.example.grpc.hello.GreeterGrpc;
import dev.example.grpc.hello.HelloReply;
import dev.example.grpc.hello.HelloRequest;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import net.devh.boot.grpc.client.security.CallCredentialsHelper;
import net.devh.boot.grpc.server.config.GrpcServerProperties;

@SpringBootTest
class CallCredentialSecurityTest {

    private static Path certs;

    @DynamicPropertySource
    static void certificates(DynamicPropertyRegistry registry) {
        certs = generateCertificates();
        registry.add("grpc.server.port", () -> "0");
        registry.add("grpc.server.security.certificate-chain", () -> certs.resolve("server.crt").toUri().toString());
        registry.add("grpc.server.security.private-key", () -> certs.resolve("server.pkcs8.pem").toUri().toString());
        registry.add("grpc.server.security.trust-cert-collection", () -> certs.resolve("ca.crt").toUri().toString());
    }

    @AfterAll
    static void deleteCertificates() throws IOException {
        if (certs == null || !Files.exists(certs)) {
            return;
        }
        Files.walk(certs)
                .sorted((left, right) -> right.compareTo(left))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                });
    }

    @Autowired
    private GrpcServerProperties serverProperties;

    @Test
    void bearerCallCredentialAndSpringSecurity() throws Exception {
        HelloRequest request = HelloRequest.newBuilder().setName("Codespaces").build();
        ManagedChannel channel = NettyChannelBuilder.forAddress("localhost", serverProperties.getPort())
                .sslContext(GrpcSslContexts.forClient()
                        .trustManager(certs.resolve("ca.crt").toFile())
                        .keyManager(certs.resolve("client.crt").toFile(), certs.resolve("client.pkcs8.pem").toFile())
                        .build())
                .build();
        try {
            GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);
            assertEquals(Status.Code.UNAUTHENTICATED, statusOf(stub, request, DemoTokens.INVALID));
            assertEquals(Status.Code.PERMISSION_DENIED, statusOf(stub, request, DemoTokens.OBSERVER));
            HelloReply reply = stub.withCallCredentials(CallCredentialsHelper.bearerAuth(DemoTokens.GREETER))
                    .sayHello(request);
            assertEquals("Hello, Codespaces", reply.getMessage());
        } finally {
            channel.shutdownNow();
        }
    }

    private static Status.Code statusOf(GreeterGrpc.GreeterBlockingStub stub, HelloRequest request, String token) {
        try {
            stub.withCallCredentials(CallCredentialsHelper.bearerAuth(token)).sayHello(request);
            return Status.Code.OK;
        } catch (StatusRuntimeException ex) {
            return ex.getStatus().getCode();
        }
    }

    private static Path generateCertificates() {
        try {
            Path dir = Files.createTempDirectory("grpc-mtls");
            openssl(dir,
                    "req", "-x509", "-newkey", "rsa:2048", "-sha256", "-days", "1", "-nodes",
                    "-keyout", "ca.key", "-out", "ca.crt", "-subj", "/CN=demo-ca");
            openssl(dir,
                    "req", "-newkey", "rsa:2048", "-nodes", "-keyout", "server.key", "-out", "server.csr",
                    "-subj", "/CN=server");
            Files.write(dir.resolve("server.ext"),
                    ("subjectAltName=DNS:localhost,IP:127.0.0.1\n"
                            + "extendedKeyUsage=serverAuth\n"
                            + "keyUsage=digitalSignature,keyEncipherment\n"
                            + "basicConstraints=CA:FALSE\n").getBytes("UTF-8"));
            openssl(dir,
                    "x509", "-req", "-in", "server.csr", "-CA", "ca.crt", "-CAkey", "ca.key", "-CAcreateserial",
                    "-out", "server.crt", "-days", "1", "-sha256", "-extfile", "server.ext");
            openssl(dir, "pkcs8", "-topk8", "-nocrypt", "-in", "server.key", "-out", "server.pkcs8.pem");
            Files.write(dir.resolve("client.ext"),
                    ("extendedKeyUsage=clientAuth\n"
                            + "keyUsage=digitalSignature\n"
                            + "basicConstraints=CA:FALSE\n").getBytes("UTF-8"));
            openssl(dir,
                    "req", "-newkey", "rsa:2048", "-nodes", "-keyout", "client.key", "-out", "client.csr",
                    "-subj", "/CN=demo-client");
            openssl(dir,
                    "x509", "-req", "-in", "client.csr", "-CA", "ca.crt", "-CAkey", "ca.key", "-CAcreateserial",
                    "-out", "client.crt", "-days", "1", "-sha256", "-extfile", "client.ext");
            openssl(dir, "pkcs8", "-topk8", "-nocrypt", "-in", "client.key", "-out", "client.pkcs8.pem");
            return dir;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void openssl(Path dir, String... args) throws IOException {
        String[] command = new String[args.length + 1];
        command[0] = "openssl";
        System.arraycopy(args, 0, command, 1, args.length);
        Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        String output = read(process.getInputStream());
        try {
            if (process.waitFor() != 0) {
                throw new IllegalStateException(output);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(output, ex);
        }
    }

    private static String read(InputStream input) throws IOException {
        byte[] buffer = new byte[4096];
        StringBuilder text = new StringBuilder();
        int count;
        while ((count = input.read(buffer)) >= 0) {
            text.append(new String(buffer, 0, count, "UTF-8"));
        }
        return text.toString();
    }
}
