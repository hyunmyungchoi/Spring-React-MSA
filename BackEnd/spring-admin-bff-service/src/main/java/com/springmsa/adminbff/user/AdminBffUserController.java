package com.springmsa.adminbff.user;

import com.springmsa.adminbff.common.proxy.AdminBffApiProxyClient;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class AdminBffUserController {

    private final AdminBffApiProxyClient adminBffApiProxyClient;
    private final ObjectMapper objectMapper;

    @Value("${admin-bff.api.user-me-uri}")
    private String userMeUri;

    public AdminBffUserController(
            AdminBffApiProxyClient adminBffApiProxyClient,
            ObjectMapper objectMapper
    ) {
        this.adminBffApiProxyClient = adminBffApiProxyClient;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/user/me")
    public ResponseEntity<Map<String, Object>> me(HttpSession session) {
        try {
            String responseBody = adminBffApiProxyClient.get(session, userMeUri);
            return ResponseEntity.ok(parseJsonObject(responseBody));

        } catch (ResponseStatusException e) {
            return ResponseEntity
                    .status(e.getStatusCode())
                    .body(errorResponse(
                            e.getStatusCode().value(),
                            resolveMessage(e, "Admin BFF session is invalid")
                    ));

        } catch (ResourceAccessException e) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(errorResponse(
                            HttpStatus.SERVICE_UNAVAILABLE.value(),
                            "User service is unavailable"
                    ));

        } catch (RestClientResponseException e) {
            return ResponseEntity
                    .status(e.getStatusCode())
                    .body(errorResponse(
                            e.getStatusCode().value(),
                            "User service request failed"
                    ));
        }
    }

    private Map<String, Object> parseJsonObject(String responseBody) {
        try {
            Object parsed = objectMapper.readValue(responseBody, Map.class);

            if (!(parsed instanceof Map<?, ?> parsedMap)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "User service response is not a JSON object"
                );
            }

            Map<String, Object> response = new LinkedHashMap<>();

            parsedMap.forEach((key, value) ->
                    response.put(String.valueOf(key), value)
            );

            return response;

        } catch (ResponseStatusException e) {
            throw e;

        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Failed to parse user service response",
                    e
            );
        }
    }

    private Map<String, Object> errorResponse(int status, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("status", status);
        response.put("message", message);

        return response;
    }

    private String resolveMessage(ResponseStatusException e, String fallbackMessage) {
        String reason = e.getReason();

        if (reason == null || reason.isBlank()) {
            return fallbackMessage;
        }

        return reason;
    }
}