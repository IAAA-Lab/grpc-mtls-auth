package dev.example.grpc.client;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

import dev.example.grpc.hello.DemoTokens;
import dev.example.grpc.hello.GreeterGrpc;
import dev.example.grpc.hello.HelloReply;
import dev.example.grpc.hello.HelloRequest;
import io.grpc.CallCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import net.devh.boot.grpc.client.inject.GrpcClient;
import net.devh.boot.grpc.client.security.CallCredentialsHelper;

@Component
public class DemoRunner implements CommandLineRunner, ExitCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(DemoRunner.class);
    private static final int ATTEMPTS = 40;

    private final GreeterGrpc.GreeterBlockingStub trustedStub;
    private final String host;
    private final int port;
    private int exitCode = 1;

    public DemoRunner(
            @GrpcClient("greeter") GreeterGrpc.GreeterBlockingStub trustedStub,
            @Value("${app.grpc.host}") String host,
            @Value("${app.grpc.port}") int port) {
        this.trustedStub = trustedStub;
        this.host = host;
        this.port = port;
    }

    @Override
    public void run(String... args) throws Exception {
        HelloRequest request = HelloRequest.newBuilder().setName("Codespaces").build();
        if (!rejectedStranger(request)) {
            return;
        }
        if (!expectStatus(request, CallCredentialsHelper.bearerAuth(DemoTokens.INVALID),
                Status.Code.UNAUTHENTICATED, "bearer token rejected")) {
            return;
        }
        if (!expectStatus(request, CallCredentialsHelper.bearerAuth(DemoTokens.OBSERVER),
                Status.Code.PERMISSION_DENIED, "spring security denied the role")) {
            return;
        }
        HelloReply reply = trustedHello(request, CallCredentialsHelper.bearerAuth(DemoTokens.GREETER));
        if (reply == null) {
            return;
        }
        if (!"Hello, Codespaces".equals(reply.getMessage())) {
            log.error("unexpected reply: {}", reply.getMessage());
            return;
        }
        log.info("call credential and spring security succeeded: {}", reply.getMessage());
        exitCode = 0;
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    private boolean rejectedStranger(HelloRequest request) throws Exception {
        ManagedChannel channel = strangerChannel();
        try {
            GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);
            for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
                try {
                    HelloReply reply = stub.sayHello(request);
                    log.error("stranger certificate was accepted: {}", reply.getMessage());
                    return false;
                } catch (StatusRuntimeException ex) {
                    if (isHandshakeFailure(ex)) {
                        log.info("stranger certificate rejected: {}", ex.getStatus());
                        return true;
                    }
                    if (isRetryable(ex) && attempt < ATTEMPTS) {
                        log.info("server not ready ({}/{})", attempt, ATTEMPTS);
                        Thread.sleep(1000L);
                        continue;
                    }
                    log.error("stranger call failed for an unexpected reason: {}", ex.getStatus());
                    return false;
                }
            }
            return false;
        } finally {
            channel.shutdownNow();
        }
    }

    private boolean expectStatus(HelloRequest request, CallCredentials credentials, Status.Code expected, String label)
            throws InterruptedException {
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                HelloReply reply = trustedStub.withCallCredentials(credentials).sayHello(request);
                log.error("{} was accepted: {}", label, reply.getMessage());
                return false;
            } catch (StatusRuntimeException ex) {
                if (ex.getStatus().getCode() == expected) {
                    log.info("{}: {}", label, ex.getStatus());
                    return true;
                }
                if (isRetryable(ex) && attempt < ATTEMPTS) {
                    log.info("{} waiting ({}/{})", label, attempt, ATTEMPTS);
                    Thread.sleep(1000L);
                    continue;
                }
                log.error("{} failed unexpectedly: {}", label, ex.getStatus());
                return false;
            }
        }
        return false;
    }

    private HelloReply trustedHello(HelloRequest request, CallCredentials credentials) throws InterruptedException {
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                return trustedStub.withCallCredentials(credentials).sayHello(request);
            } catch (StatusRuntimeException ex) {
                if (isRetryable(ex) && attempt < ATTEMPTS) {
                    log.info("trusted call waiting ({}/{}): {}", attempt, ATTEMPTS, ex.getStatus().getCode());
                    Thread.sleep(1000L);
                    continue;
                }
                log.error("trusted call failed: {}", ex.getStatus());
                return null;
            }
        }
        return null;
    }

    private ManagedChannel strangerChannel() throws Exception {
        File trust = new File("/certs/ca.crt");
        File certificate = new File("/certs/stranger.crt");
        File key = new File("/certs/stranger.pkcs8.pem");
        return NettyChannelBuilder.forAddress(host, port)
                .sslContext(GrpcSslContexts.forClient()
                        .trustManager(trust)
                        .keyManager(certificate, key)
                        .build())
                .build();
    }

    private static boolean isRetryable(StatusRuntimeException ex) {
        return ex.getStatus().getCode() == Status.Code.UNAVAILABLE && !isHandshakeFailure(ex);
    }

    private static boolean isHandshakeFailure(StatusRuntimeException ex) {
        StringBuilder text = new StringBuilder();
        text.append(String.valueOf(ex.getStatus().getDescription()));
        Throwable cause = ex.getCause();
        while (cause != null) {
            text.append(' ').append(cause.getClass().getName());
            text.append(' ').append(String.valueOf(cause.getMessage()));
            cause = cause.getCause();
        }
        String lower = text.toString().toLowerCase();
        return lower.contains("handshake")
                || lower.contains("certificate")
                || lower.contains("ssl")
                || lower.contains("tls");
    }
}
