package com.springmsa.userservice.health.controller;

import com.springmsa.common.web.response.MsaResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthCheckController {

    @GetMapping("/health")
    public MsaResponse<Map<String, String>> health() {
        return MsaResponse.ok(Map.of(
                "service", "spring-user-service",
                "status", "UP"
        ));
    }
}
