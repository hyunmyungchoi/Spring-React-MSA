package com.springmsa.userservice.user.controller;

import com.springmsa.common.web.response.MsaResponse;
import com.springmsa.userservice.user.dto.AuthUserResponse;
import com.springmsa.userservice.user.service.InternalAuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/auth")
public class InternalAuthController {

    private final InternalAuthService internalAuthService;

    public InternalAuthController(InternalAuthService internalAuthService) {
        this.internalAuthService = internalAuthService;
    }

    @GetMapping("/users/{loginId}")
    public MsaResponse<AuthUserResponse> findAuthUserByLoginId(@PathVariable String loginId) {
        return MsaResponse.ok(internalAuthService.findAuthUserByLoginId(loginId));
    }

    @GetMapping("/users/whatsapp/{whatsappNumber}")
    public MsaResponse<AuthUserResponse> getUserByWhatsappNumber(@PathVariable String whatsappNumber) {
        return MsaResponse.ok(internalAuthService.findAuthUserByWhatsappNumber(whatsappNumber));
    }

    @GetMapping("/users/email/{email}")
    public MsaResponse<AuthUserResponse> getUserByEmail(@PathVariable String email) {
        return MsaResponse.ok(internalAuthService.findAuthUserByEmail(email));
    }
}
