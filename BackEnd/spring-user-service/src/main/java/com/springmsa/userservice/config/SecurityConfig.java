package com.springmsa.userservice.config;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health", "/db-health").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers("/api/user/me").authenticated()
                        .requestMatchers("/api/user/admin/**").hasAuthority("ROLE_ADMIN")
                        .anyRequest().denyAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                );

        return http.build();
    }

    private Converter<@NonNull Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");

            Collection<SimpleGrantedAuthority> authorities = roles == null
                    ? List.of()
                    : roles.stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();

            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        };
    }
}