package com.springmsa.adminbff.registration.dto;

import com.springmsa.adminbff.client.dto.AdminUserCreateResponse;

import java.util.Set;

public record AdminRegistrationResponse(
        Long userId,
        String loginId,
        String email,
        String username,
        boolean enabled,
        Set<String> roles
) {

    public static AdminRegistrationResponse from(AdminUserCreateResponse response) {
        return new AdminRegistrationResponse(
                response.userId(),
                response.loginId(),
                response.email(),
                response.username(),
                response.enabled(),
                response.roles()
        );
    }
}
