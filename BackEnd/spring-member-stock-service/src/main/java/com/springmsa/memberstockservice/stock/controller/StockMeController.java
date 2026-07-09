package com.springmsa.memberstockservice.stock.controller;

import com.springmsa.memberstockservice.stock.service.StockMeService;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class StockMeController {

    private final StockMeService stockMeService;

    public StockMeController(StockMeService stockMeService) {
        this.stockMeService = stockMeService;
    }

    @GetMapping("/api/stock/me")
    public Map<String, Object> me(JwtAuthenticationToken authentication) {
        return stockMeService.me(authentication);
    }
}
