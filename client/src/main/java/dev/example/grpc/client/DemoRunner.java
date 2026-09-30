package dev.example.grpc.client;

import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.grpc.client.interceptor.security.BearerTokenAuthenticationInterceptor;
import org.springframework.stereotype.Component;

import dev.example.grpc.hello.DemoTokens;
import dev.example.grpc.hello.GreeterGrpc;
import dev.example.grpc.hello.HelloRequest;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthGrpc;

@Component
public class DemoRunner implements CommandLineRunner, ExitCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(DemoRunner.class);
    private static final HelloRequest REQUEST = HelloRequest.newBuilder().setName("Codespaces").build();
    private static final long STARTUP_SECONDS = 60;
    private static final long CALL_SECONDS = 5;

    private final ManagedChannel greeterChannel;
    private final ManagedChannel strangerChannel;
    private int exitCode = 1;

    public DemoRunner(GrpcChannelFactory channels) {
        this.greeterChannel = channels.createChannel("greeter");
        this.strangerChannel = channels.createChannel("stranger");
    }

    @Override
    public void run(String... args) {
        waitForServer();
        if (rejectsStranger()
                && expectStatus(DemoTokens.INVALID, Status.Code.UNAUTHENTICATED, "bearer token rechazado")
                && expectStatus(DemoTokens.OBSERVER, Status.Code.PERMISSION_DENIED, "Spring Security denegó el rol")
                && greets()) {
            exitCode = 0;
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    private void waitForServer() {
        var status = HealthGrpc.newBlockingStub(greeterChannel)
                .withWaitForReady()
                .withDeadlineAfter(STARTUP_SECONDS, TimeUnit.SECONDS)
                .check(HealthCheckRequest.getDefaultInstance())
                .getStatus();
        log.info("servidor listo: {}", status);
    }

    private boolean rejectsStranger() {
        try {
            var reply = stub(strangerChannel, DemoTokens.GREETER).sayHello(REQUEST);
            log.error("se aceptó el certificado ajeno: {}", reply.getMessage());
            return false;
        } catch (StatusRuntimeException ex) {
            if (ex.getStatus().getCode() == Status.Code.UNAVAILABLE
                    && NestedExceptionUtils.getMostSpecificCause(ex) instanceof SSLException tls) {
                log.info("certificado ajeno rechazado en el handshake TLS: {}", tls.getMessage());
                return true;
            }
            log.error("la llamada con certificado ajeno falló por otro motivo: {}", ex.getStatus());
            return false;
        }
    }

    private boolean expectStatus(String token, Status.Code expected, String label) {
        try {
            var reply = stub(greeterChannel, token).sayHello(REQUEST);
            log.error("{} no ocurrió: {}", label, reply.getMessage());
            return false;
        } catch (StatusRuntimeException ex) {
            if (ex.getStatus().getCode() == expected) {
                log.info("{}: {}", label, ex.getStatus());
                return true;
            }
            log.error("{} falló de forma inesperada: {}", label, ex.getStatus());
            return false;
        }
    }

    private boolean greets() {
        try {
            var message = stub(greeterChannel, DemoTokens.GREETER).sayHello(REQUEST).getMessage();
            if (!"Hola, Codespaces".equals(message)) {
                log.error("respuesta inesperada: {}", message);
                return false;
            }
            log.info("mTLS, bearer token y rol correctos: {}", message);
            return true;
        } catch (StatusRuntimeException ex) {
            log.error("falló la llamada de confianza: {}", ex.getStatus());
            return false;
        }
    }

    private static GreeterGrpc.GreeterBlockingStub stub(ManagedChannel channel, String token) {
        return GreeterGrpc.newBlockingStub(channel)
                .withInterceptors(new BearerTokenAuthenticationInterceptor(token))
                .withDeadlineAfter(CALL_SECONDS, TimeUnit.SECONDS);
    }
}
