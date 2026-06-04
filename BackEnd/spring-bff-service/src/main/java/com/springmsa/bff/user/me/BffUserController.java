package com.springmsa.bff.user.me;

import com.springmsa.bff.common.proxy.BffApiProxyClient;
import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@NullMarked
@RestController
public class BffUserController {

    private final BffApiProxyClient bffApiProxyClient;

    @Value("${bff.api.user-me-uri}")
    private String userMeUri;

    public BffUserController(BffApiProxyClient bffApiProxyClient) {
        this.bffApiProxyClient = bffApiProxyClient;
    }

    @GetMapping("/bff/user/me")
    public ResponseEntity<String> me(HttpSession session) {
        String responseBody = bffApiProxyClient.get(session, userMeUri);
        return ResponseEntity.ok(responseBody);
    }
}