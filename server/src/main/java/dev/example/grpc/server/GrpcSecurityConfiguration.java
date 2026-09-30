package dev.example.grpc.server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.grpc.server.security.AuthenticationProcessInterceptor;
import org.springframework.grpc.server.security.GrpcSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import dev.example.grpc.hello.DemoTokens;
import dev.example.grpc.hello.GreeterGrpc;
import io.grpc.health.v1.HealthGrpc;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class GrpcSecurityConfiguration {

    @Bean
    @GlobalServerInterceptor
    AuthenticationProcessInterceptor grpcSecurityInterceptor(GrpcSecurity grpc, DemoTokenIntrospector introspector)
            throws Exception {
        return grpc
                .authorizeRequests(requests -> requests
                        .methods(GreeterGrpc.getSayHelloMethod().getFullMethodName())
                        .hasAuthority(DemoTokens.ROLE_GREETER)
                        .methods(HealthGrpc.SERVICE_NAME + "/*").permitAll()
                        .allRequests().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .opaqueToken(opaqueToken -> opaqueToken.introspector(introspector)))
                .build();
    }
}
