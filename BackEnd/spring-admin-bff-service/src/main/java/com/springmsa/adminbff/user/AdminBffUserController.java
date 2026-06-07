package com.springmsa.adminbff.user;

import com.springmsa.adminbff.common.proxy.AdminBffApiProxyClient;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminBffUserController {

    private final AdminBffApiProxyClient adminBffApiProxyClient;

    @Value("${admin-bff.api.user-me-uri}")
    private String userMeUri;

    public AdminBffUserController(AdminBffApiProxyClient adminBffApiProxyClient) {
        this.adminBffApiProxyClient = adminBffApiProxyClient;
    }

    @GetMapping("/user/me")
    public ResponseEntity<String> me(HttpSession session) {
        String responseBody = adminBffApiProxyClient.get(session, userMeUri);
        return ResponseEntity.ok(responseBody);
    }
}