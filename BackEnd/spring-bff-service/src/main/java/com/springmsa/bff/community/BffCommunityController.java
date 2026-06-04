package com.springmsa.bff.community;

import com.springmsa.bff.common.proxy.BffApiProxyClient;
import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@NullMarked
@RestController
public class BffCommunityController {

    private final BffApiProxyClient bffApiProxyClient;

    @Value("${bff.api.community-me-uri}")
    private String communityMeUri;

    public BffCommunityController(BffApiProxyClient bffApiProxyClient) {
        this.bffApiProxyClient = bffApiProxyClient;
    }

    @GetMapping("/bff/community/me")
    public ResponseEntity<String> me(HttpSession session) {
        String responseBody = bffApiProxyClient.get(session, communityMeUri);
        return ResponseEntity.ok(responseBody);
    }
}