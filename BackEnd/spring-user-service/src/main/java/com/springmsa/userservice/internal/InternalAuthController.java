package com.springmsa.userservice.internal;

import com.springmsa.userservice.api.dto.AuthUserResponse;
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
    public AuthUserResponse findAuthUserByLoginId(@PathVariable String loginId) {
        return internalAuthService.findAuthUserByLoginId(loginId);
    }

    @GetMapping("/users/whatsapp/{whatsappNumber}")
    public AuthUserResponse getUserByWhatsappNumber(@PathVariable String whatsappNumber) {
        return internalAuthService.findAuthUserByWhatsappNumber(whatsappNumber);
    }

    @GetMapping("/users/email/{email}")
    public AuthUserResponse getUserByEmail(@PathVariable String email) {
        return internalAuthService.findAuthUserByEmail(email);
    }
}
