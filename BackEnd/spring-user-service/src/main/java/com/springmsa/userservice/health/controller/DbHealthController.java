package com.springmsa.userservice.health.controller;

import com.springmsa.common.web.response.MsaResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DbHealthController {

    private final JdbcTemplate jdbcTemplate;

    public DbHealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/db-health")
    public MsaResponse<Map<String, Object>> dbHealth() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return MsaResponse.ok(Map.of(
                "service", "spring-user-service",
                "status", "UP",
                "database", result
        ));
    }
}
