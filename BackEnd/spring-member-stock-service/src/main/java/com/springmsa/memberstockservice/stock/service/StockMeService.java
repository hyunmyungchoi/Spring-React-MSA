package com.springmsa.memberstockservice.stock.service;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class StockMeService {

    public Map<String, Object> me(JwtAuthenticationToken authentication) {
        Jwt jwt = authentication.getToken();

        return Map.of(
                "service", "spring-member-stock-service",
                "subject", jwt.getSubject(),
                "userId", jwt.getClaim("user_id"),
                "loginId", jwt.getClaim("login_id"),
                "email", jwt.getClaim("email"),
                "roles", jwt.getClaim("roles")
        );
    }
}
