package com.springmsa.adminbff.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Component
public class AdminJwtClaimReader {

    private final ObjectMapper objectMapper;

    public AdminJwtClaimReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<?, ?> readClaims(String jwt) {
        if (jwt.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Empty access token");
        }

        String[] parts = jwt.split("\\.");

        if (parts.length < 2) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid access token format");
        }

        try {
            String payloadJson = new String(
                    Base64.getUrlDecoder().decode(parts[1]),
                    StandardCharsets.UTF_8
            );

            Object claimsObj = objectMapper.readValue(payloadJson, Map.class);

            if (!(claimsObj instanceof Map<?, ?> claims)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid access token claims");
            }

            return claims;

        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid access token", e);
        }
    }
}