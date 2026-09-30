package dev.example.grpc.server;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.stereotype.Component;

import dev.example.grpc.hello.DemoTokens;

@Component
class DemoTokenIntrospector implements OpaqueTokenIntrospector {

    private record Grant(String subject, String role) {
    }

    private static final Map<String, Grant> GRANTS = Map.of(
            DemoTokens.GREETER, new Grant("greeter", DemoTokens.ROLE_GREETER),
            DemoTokens.OBSERVER, new Grant("observer", DemoTokens.ROLE_OBSERVER));

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        var grant = GRANTS.get(token);
        if (grant == null) {
            throw new BadOpaqueTokenException("bearer token no válido");
        }
        return new DefaultOAuth2AuthenticatedPrincipal(
                grant.subject(),
                Map.of("sub", grant.subject()),
                List.of(new SimpleGrantedAuthority(grant.role())));
    }
}
