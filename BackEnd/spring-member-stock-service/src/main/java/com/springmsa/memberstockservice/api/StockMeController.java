package com.springmsa.memberstockservice.api;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class StockMeController {

    @GetMapping("/api/stock/me")
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
