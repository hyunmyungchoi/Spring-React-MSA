package com.springmsa.authserver.login.dto;

import com.springmsa.authserver.user.dto.AuthUserResponse;

import java.util.Set;

public record LoginUserResponse(
        Long userId,
        String loginId,
        String email,
        String username,
        Set<String> roles
) {
    public static LoginUserResponse from(AuthUserResponse user) {
        return new LoginUserResponse(
                user.userId(),
                user.loginId(),
                user.email(),
                user.username(),
                user.roles()
        );
    }
}
