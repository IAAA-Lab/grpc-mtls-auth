package dev.example.grpc.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.grpc.client.interceptor.security.BasicAuthenticationInterceptor;
import org.springframework.grpc.client.interceptor.security.BearerTokenAuthenticationInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import dev.example.grpc.hello.DemoTokens;
import dev.example.grpc.hello.GreeterGrpc;
import dev.example.grpc.hello.HelloRequest;
import io.grpc.ClientInterceptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.reflection.v1.ServerReflectionGrpc;
import io.grpc.reflection.v1.ServerReflectionRequest;
import io.grpc.reflection.v1.ServerReflectionResponse;
import io.grpc.stub.StreamObserver;

@SpringBootTest(properties = {
        "spring.grpc.server.port=0",
        "spring.grpc.client.channels.greeter.address=static://localhost:${local.grpc.port}",
        "spring.grpc.client.channels.greeter.negotiation-type=tls",
        "spring.grpc.client.channels.greeter.ssl.bundle=client",
        "spring.grpc.client.channels.stranger.address=static://localhost:${local.grpc.port}",
        "spring.grpc.client.channels.stranger.negotiation-type=tls",
        "spring.grpc.client.channels.stranger.ssl.bundle=stranger",
        "spring.ssl.bundle.pem.client.keystore.certificate=file:${CERTS_DIR}/client.crt",
        "spring.ssl.bundle.pem.client.keystore.private-key=file:${CERTS_DIR}/client.pkcs8.pem",
        "spring.ssl.bundle.pem.client.truststore.certificate=file:${CERTS_DIR}/ca.crt",
        "spring.ssl.bundle.pem.stranger.keystore.certificate=file:${CERTS_DIR}/stranger.crt",
        "spring.ssl.bundle.pem.stranger.keystore.private-key=file:${CERTS_DIR}/stranger.pkcs8.pem",
        "spring.ssl.bundle.pem.stranger.truststore.certificate=file:${CERTS_DIR}/ca.crt" })
class GrpcSecurityTest {

    private static final HelloRequest REQUEST = HelloRequest.newBuilder().setName("Codespaces").build();

    @TempDir
    static Path certs;

    @Autowired
    private GrpcChannelFactory channels;

    @Autowired
    private GreeterService greeterService;

    @DynamicPropertySource
    static void certificates(DynamicPropertyRegistry registry) throws Exception {
        var exit = new ProcessBuilder("sh", "../scripts/generate-certs.sh", certs.toString())
                .inheritIO()
                .start()
                .waitFor();
        if (exit != 0) {
            throw new IllegalStateException("generate-certs.sh terminó con código " + exit);
        }
        registry.add("CERTS_DIR", certs::toString);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void greeterTokenIsAccepted() {
        var reply = greeter(bearer(DemoTokens.GREETER)).sayHello(REQUEST);
        assertThat(reply.getMessage()).isEqualTo("Hola, Codespaces");
    }

    @Test
    void unknownTokenIsUnauthenticated() {
        assertThat(statusOf(greeter(bearer(DemoTokens.INVALID)))).isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void missingTokenIsUnauthenticated() {
        assertThat(statusOf(greeter())).isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void basicCredentialsAreNotAccepted() {
        assertThat(statusOf(greeter(new BasicAuthenticationInterceptor("user", "password"))))
                .isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void observerRoleIsDenied() {
        assertThat(statusOf(greeter(bearer(DemoTokens.OBSERVER)))).isEqualTo(Status.Code.PERMISSION_DENIED);
    }

    @Test
    void certificateFromAnotherCaFailsTheHandshake() {
        var stub = GreeterGrpc.newBlockingStub(channels.createChannel("stranger"))
                .withInterceptors(bearer(DemoTokens.GREETER))
                .withDeadlineAfter(5, TimeUnit.SECONDS);
        assertThatThrownBy(() -> stub.sayHello(REQUEST))
                .isInstanceOfSatisfying(StatusRuntimeException.class, ex -> {
                    assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
                    assertThat(ex).hasRootCauseInstanceOf(SSLException.class);
                });
    }

    @Test
    void healthIsOpenWithoutToken() {
        var health = HealthGrpc.newBlockingStub(channels.createChannel("greeter"))
                .withDeadlineAfter(5, TimeUnit.SECONDS);
        assertThat(health.check(HealthCheckRequest.getDefaultInstance()).getStatus())
                .isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
    }

    @Test
    void reflectionIsNotExposed() {
        var outcome = new CompletableFuture<Throwable>();
        var requests = ServerReflectionGrpc.newStub(channels.createChannel("greeter"))
                .withDeadlineAfter(5, TimeUnit.SECONDS)
                .serverReflectionInfo(new StreamObserver<ServerReflectionResponse>() {
                    @Override
                    public void onNext(ServerReflectionResponse value) {
                        outcome.complete(null);
                    }

                    @Override
                    public void onError(Throwable t) {
                        outcome.complete(t);
                    }

                    @Override
                    public void onCompleted() {
                        outcome.complete(null);
                    }
                });
        requests.onNext(ServerReflectionRequest.newBuilder().setListServices("").build());
        requests.onCompleted();
        assertThat(Status.fromThrowable(outcome.join()).getCode()).isEqualTo(Status.Code.UNIMPLEMENTED);
    }

    @Test
    void preAuthorizeGuardsTheMethodItself() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("observer", null, DemoTokens.ROLE_OBSERVER));
        assertThatThrownBy(() -> greeterService.sayHello(REQUEST, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    private GreeterGrpc.GreeterBlockingStub greeter(ClientInterceptor... interceptors) {
        return GreeterGrpc.newBlockingStub(channels.createChannel("greeter"))
                .withInterceptors(interceptors)
                .withDeadlineAfter(5, TimeUnit.SECONDS);
    }

    private static ClientInterceptor bearer(String token) {
        return new BearerTokenAuthenticationInterceptor(token);
    }

    private static Status.Code statusOf(GreeterGrpc.GreeterBlockingStub stub) {
        try {
            stub.sayHello(REQUEST);
            return Status.Code.OK;
        } catch (StatusRuntimeException ex) {
            return ex.getStatus().getCode();
        }
    }
}
