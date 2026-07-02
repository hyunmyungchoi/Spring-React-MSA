package com.springmsa.membercommunityservice.api;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class CommunityMeController {

    @GetMapping("/health")
    public String health() {
        return "spring-member-community-service is running";
    }

    @GetMapping("/api/community/me")
    public Map<String, Object> me(Authentication authentication) {
        JwtAuthenticationToken jwtAuthentication = (JwtAuthenticationToken) authentication;
        Jwt jwt = jwtAuthentication.getToken();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("subject", jwt.getSubject());
        response.put("userId", jwt.getClaim("user_id"));
        response.put("loginId", jwt.getClaim("login_id"));
        response.put("email", jwt.getClaim("email"));
        response.put("roles", jwt.getClaim("roles"));
        response.put("authorities", authentication.getAuthorities());
        response.put("issuer", jwt.getIssuer().toString());
        response.put("audience", jwt.getAudience());

        return response;
    }
}