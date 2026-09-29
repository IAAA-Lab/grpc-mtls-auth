package dev.example.grpc.server;

import java.util.Collections;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

import dev.example.grpc.hello.DemoTokens;
import net.devh.boot.grpc.server.security.authentication.BearerAuthenticationReader;
import net.devh.boot.grpc.server.security.authentication.GrpcAuthenticationReader;

@Configuration
@EnableGlobalMethodSecurity(securedEnabled = true, proxyTargetClass = true)
public class GrpcSecurityConfiguration {

    @Bean
    AuthenticationManager authenticationManager() {
        return new ProviderManager(Collections.<AuthenticationProvider>singletonList(new DemoTokenAuthenticationProvider()));
    }

    @Bean
    GrpcAuthenticationReader authenticationReader() {
        // PreAuthenticatedAuthenticationToken: el bearer token ya está en el metadata.
        // En gRPC lo habitual: bearer token (access token OAuth 2.0, a menudo JWT) o client certificate (mTLS).
        return new BearerAuthenticationReader(token -> new PreAuthenticatedAuthenticationToken(token, null));
    }

    static final class DemoTokenAuthenticationProvider implements AuthenticationProvider {

        @Override
        public Authentication authenticate(Authentication authentication) {
            String token = String.valueOf(authentication.getPrincipal());
            if (DemoTokens.GREETER.equals(token)) {
                return authenticated("greeter", DemoTokens.ROLE_GREETER);
            }
            if (DemoTokens.OBSERVER.equals(token)) {
                return authenticated("observer", DemoTokens.ROLE_OBSERVER);
            }
            throw new BadCredentialsException("bearer token no válido");
        }

        @Override
        public boolean supports(Class<?> authentication) {
            return PreAuthenticatedAuthenticationToken.class.isAssignableFrom(authentication);
        }

        private static Authentication authenticated(String name, String role) {
            return new UsernamePasswordAuthenticationToken(
                    name,
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority(role)));
        }
    }
}
