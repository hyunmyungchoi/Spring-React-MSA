package com.springmsa.memberbff.userbff.controller;

import com.springmsa.common.web.response.MsaResponse;
import com.springmsa.memberbff.userbff.dto.CurrentUserResponse;
import com.springmsa.memberbff.userbff.service.UserBffService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserBffController {

    private final UserBffService userBffService;

    @GetMapping("/user/me")
    public ResponseEntity<MsaResponse<CurrentUserResponse>> me(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(MsaResponse.ok(userBffService.getCurrentUser(authentication, request, response)));
    }
}
