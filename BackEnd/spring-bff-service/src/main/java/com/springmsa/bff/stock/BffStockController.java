package com.springmsa.bff.stock;

import com.springmsa.bff.common.proxy.BffApiProxyClient;
import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@NullMarked
@RestController
public class BffStockController {

    private final BffApiProxyClient bffApiProxyClient;

    @Value("${bff.api.stock-me-uri}")
    private String stockMeUri;

    public BffStockController(BffApiProxyClient bffApiProxyClient) {
        this.bffApiProxyClient = bffApiProxyClient;
    }

    @GetMapping("/bff/stock/me")
    public ResponseEntity<String> me(HttpSession session) {
        String responseBody = bffApiProxyClient.get(session, stockMeUri);
        return ResponseEntity.ok(responseBody);
    }
}