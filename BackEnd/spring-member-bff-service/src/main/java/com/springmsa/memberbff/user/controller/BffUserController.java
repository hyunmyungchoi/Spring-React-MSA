package com.springmsa.memberbff.user.controller;

import com.springmsa.common.web.response.MsaResponse;
import com.springmsa.memberbff.user.dto.CurrentUserResponse;
import com.springmsa.memberbff.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BffUserController {

    private final UserService userService;

    @GetMapping("/user/me")
    public ResponseEntity<MsaResponse<CurrentUserResponse>> me(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(MsaResponse.ok(userService.getCurrentUser(authentication, request, response)));
    }
}
