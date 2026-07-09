package com.springmsa.userservice.user.controller;

import com.springmsa.common.web.response.MsaResponse;
import com.springmsa.userservice.user.dto.UserResponse;
import com.springmsa.userservice.user.service.InternalUserQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final InternalUserQueryService internalUserQueryService;

    public InternalUserController(InternalUserQueryService internalUserQueryService) {
        this.internalUserQueryService = internalUserQueryService;
    }

    @GetMapping("/login-id/{loginId}")
    public MsaResponse<UserResponse> findByLoginId(@PathVariable String loginId) {
        return MsaResponse.ok(internalUserQueryService.findByLoginId(loginId));
    }
}
