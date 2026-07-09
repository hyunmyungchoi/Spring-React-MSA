package com.springmsa.memberbff.health.controller;

import com.springmsa.common.web.response.MsaResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/health")
    public MsaResponse<Map<String, Object>> health() {
        return MsaResponse.ok(Map.of(
                "service", "spring-member-bff-service",
                "status", "UP"
        ));
    }
}
