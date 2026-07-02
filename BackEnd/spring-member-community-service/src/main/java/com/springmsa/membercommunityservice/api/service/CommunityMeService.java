package com.springmsa.membercommunityservice.api.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CommunityMeService {

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
