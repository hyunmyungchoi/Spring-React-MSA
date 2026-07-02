package com.springmsa.adminbff.session;

import com.springmsa.adminbff.common.dto.AdminApiResponse;
import com.springmsa.adminbff.session.dto.MemberPresenceEventResponse;
import com.springmsa.adminbff.session.dto.MemberSessionResponse;
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
    public ResponseEntity<AdminApiResponse<List<MemberSessionResponse>>> memberSessions() {
        return ResponseEntity.ok(AdminApiResponse.ok(memberSessionService.findMemberSessions()));
    }

    @GetMapping("/sessions/member/events")
    public ResponseEntity<AdminApiResponse<List<MemberPresenceEventResponse>>> memberPresenceEvents() {
        return ResponseEntity.ok(AdminApiResponse.ok(memberSessionService.findMemberPresenceEvents()));
    }
}
