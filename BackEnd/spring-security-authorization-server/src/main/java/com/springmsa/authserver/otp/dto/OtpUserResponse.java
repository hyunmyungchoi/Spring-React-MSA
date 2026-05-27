package com.springmsa.authserver.otp.dto;

import com.springmsa.authserver.client.dto.AuthUserResponse;

import java.util.Set;

public record OtpUserResponse(
        Long userId,
        String loginId,
        String email,
        String username,
        Set<String> roles
) {
    public static OtpUserResponse from(AuthUserResponse user) {
        return new OtpUserResponse(
                user.userId(),
                user.loginId(),
                user.email(),
                user.username(),
                user.roles()
        );
    }
}