package com.springmsa.memberbff.registration.dto;

import com.springmsa.memberbff.client.dto.UserCreateResponse;

import java.util.Set;

public record RegistrationResponse(
        Long userId,
        String loginId,
        String email,
        String username,
        boolean enabled,
        Set<String> roles
) {

    public static RegistrationResponse from(UserCreateResponse response) {
        return new RegistrationResponse(
                response.userId(),
                response.loginId(),
                response.email(),
                response.username(),
                response.enabled(),
                response.roles()
        );
    }
}
