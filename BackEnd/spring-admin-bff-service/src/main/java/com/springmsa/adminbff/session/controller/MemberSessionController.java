package com.springmsa.adminbff.session.controller;

import com.springmsa.adminbff.session.dto.MemberPresenceEventResponse;
import com.springmsa.adminbff.session.dto.MemberSessionResponse;
import com.springmsa.adminbff.session.service.MemberSessionService;
import com.springmsa.common.web.response.MsaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MemberSessionController {

    private final MemberSessionService memberSessionService;

    @GetMapping("/sessions/member")
    public ResponseEntity<MsaResponse<List<MemberSessionResponse>>> memberSessions() {
        return ResponseEntity.ok(MsaResponse.ok(memberSessionService.findMemberSessions()));
    }

    @GetMapping("/sessions/member/events")
    public ResponseEntity<MsaResponse<List<MemberPresenceEventResponse>>> memberPresenceEvents() {
        return ResponseEntity.ok(MsaResponse.ok(memberSessionService.findMemberPresenceEvents()));
    }
}
