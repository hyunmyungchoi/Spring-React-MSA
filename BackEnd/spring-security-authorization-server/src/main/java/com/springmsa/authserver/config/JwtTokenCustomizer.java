package com.springmsa.authserver.config;

import com.springmsa.authserver.security.CustomUserPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.util.List;

@Configuration
public class JwtTokenCustomizer {

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer() {
        return context -> {
            Object principal = context.getPrincipal().getPrincipal();

            if (!(principal instanceof CustomUserPrincipal userPrincipal)) {
                return;
            }

            List<String> roles = userPrincipal.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList();

            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                context.getClaims()
                        .claim("user_id", userPrincipal.getUserId())
                        .claim("login_id", userPrincipal.getLoginId())
                        .claim("email", userPrincipal.getEmail())
                        .claim("name", userPrincipal.getName())
                        .claim("username", userPrincipal.getName())
                        .claim("roles", roles);
            }

            if (OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
                context.getClaims()
                        .claim("user_id", userPrincipal.getUserId())
                        .claim("login_id", userPrincipal.getLoginId())
                        .claim("email", userPrincipal.getEmail())
                        .claim("name", userPrincipal.getName())
                        .claim("username", userPrincipal.getName())
                        .claim("roles", roles);
            }
        };
    }
}
