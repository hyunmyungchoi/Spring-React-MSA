package com.springmsa.userservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class InternalApiTokenFilter extends OncePerRequestFilter {

    private final String headerName;
    private final byte[] expectedToken;

    public InternalApiTokenFilter(String headerName, String token) {
        if (!StringUtils.hasText(headerName)) {
            throw new IllegalStateException("springmsa.internal-api.header-name must not be blank");
        }

        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("springmsa.internal-api.token must not be blank");
        }

        this.headerName = headerName;
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getServletPath().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String providedToken = request.getHeader(headerName);

        if (!matches(providedToken)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean matches(String providedToken) {
        if (!StringUtils.hasText(providedToken)) {
            return false;
        }

        return MessageDigest.isEqual(expectedToken, providedToken.getBytes(StandardCharsets.UTF_8));
    }
}
