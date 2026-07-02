package com.springmsa.memberbff.config;

import com.springmsa.memberbff.auth.BffSessionMetadataService;
import com.springmsa.memberbff.presence.MemberPresenceService;
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
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
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
    private static final String CSRF_COOKIE_NAME = "MEMBER-XSRF-TOKEN";
    private static final String CSRF_HEADER_NAME = "X-MEMBER-XSRF-TOKEN";

    @Value("${bff.frontend.redirect-uri}")
    private String frontendRedirectUri;

    private final BffSessionMetadataService bffSessionMetadataService;
    private final MemberPresenceService memberPresenceService;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        // The Member BFF uses a browser session cookie, so unsafe requests must include a CSRF token.
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(csrfTokenRequestHandler())
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

    private CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName(CSRF_COOKIE_NAME);
        repository.setHeaderName(CSRF_HEADER_NAME);
        repository.setCookiePath("/");
        return repository;
    }

    private CsrfTokenRequestAttributeHandler csrfTokenRequestHandler() {
        return new CsrfTokenRequestAttributeHandler();
    }

    private void handleLoginSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        bffSessionMetadataService.saveMemberSessionMetadata(request.getSession(), authentication);
        memberPresenceService.login(request.getSession(false), authentication);
        response.sendRedirect(frontendRedirectUri);
    }

    private void handleLoginFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        response.sendRedirect(errorRedirectUri());
    }

    private String errorRedirectUri() {
        return UriComponentsBuilder.fromUriString(frontendRedirectUri)
                .queryParam("error", SecurityConfig.OAUTH2_LOGIN_FAILED)
                .build()
                .encode()
                .toUriString();
    }
}
