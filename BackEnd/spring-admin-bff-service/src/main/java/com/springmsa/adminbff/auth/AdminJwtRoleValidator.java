package com.springmsa.adminbff.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;

@Component
public class AdminJwtRoleValidator {

    private final ObjectMapper objectMapper;

    public AdminJwtRoleValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean hasRole(String jwt, String requiredRole) {
        if (jwt.isBlank()) {
            return false;
        }

        String[] parts = jwt.split("\\.");

        if (parts.length < 2) {
            return false;
        }

        try {
            String payloadJson = new String(
                    Base64.getUrlDecoder().decode(parts[1]),
                    StandardCharsets.UTF_8
            );

            Object claimsObj = objectMapper.readValue(payloadJson, Map.class);

            if (!(claimsObj instanceof Map<?, ?> claims)) {
                return false;
            }

            Object roles = claims.get("roles");

            if (!(roles instanceof Collection<?> roleCollection)) {
                return false;
            }

            return roleCollection.stream()
                    .map(String::valueOf)
                    .anyMatch(requiredRole::equals);

        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid access token", e);
        }
    }
}