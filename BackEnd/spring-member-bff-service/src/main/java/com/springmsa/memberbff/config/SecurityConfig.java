package com.springmsa.memberbff.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/actuator/**",
            "/health",
            "/auth/me",
            "/auth/logout",
            "/oauth2/**",
            "/login/oauth2/**"
    };

    private static final String OAUTH2_LOGIN_FAILED = "oauth2_login_failed";

    @Value("${bff.frontend.redirect-uri}")
    private String frontendRedirectUri;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        // The Member BFF uses a browser session cookie, so unsafe requests must include a CSRF token.
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/registration/member").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(this::handleLoginSuccess)
                        .failureHandler(this::handleLoginFailure)
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                response.sendError(HttpStatus.UNAUTHORIZED.value())
                        )
                );

        return http.build();
    }

    private void handleLoginSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        response.sendRedirect(frontendRedirectUri);
    }

    private void handleLoginFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        response.sendRedirect(errorRedirectUri(OAUTH2_LOGIN_FAILED));
    }

    private String errorRedirectUri(String error) {
        return UriComponentsBuilder.fromUriString(frontendRedirectUri)
                .queryParam("error", error)
                .build()
                .encode()
                .toUriString();
    }
}
