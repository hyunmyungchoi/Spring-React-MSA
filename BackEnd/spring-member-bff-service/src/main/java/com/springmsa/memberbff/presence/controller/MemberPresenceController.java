package com.springmsa.memberbff.presence.controller;

import com.springmsa.common.web.response.MsaResponse;
import com.springmsa.memberbff.presence.dto.HeartbeatResponse;
import com.springmsa.memberbff.presence.service.MemberPresenceService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MemberPresenceController {

    private final MemberPresenceService memberPresenceService;

    @PostMapping("/auth/heartbeat")
    public ResponseEntity<MsaResponse<HeartbeatResponse>> heartbeat(HttpServletRequest request, Authentication authentication) {
        return ResponseEntity.ok(MsaResponse.ok(memberPresenceService.heartbeat(request.getSession(false), authentication)));
    }
}
