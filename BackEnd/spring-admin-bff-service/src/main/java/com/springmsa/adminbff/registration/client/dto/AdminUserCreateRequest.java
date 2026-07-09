package com.springmsa.adminbff.registration.client.dto;

import com.springmsa.adminbff.registration.dto.AdminRegistrationRequest;

import java.util.Set;

public record AdminUserCreateRequest(
        String loginId,
        String email,
        String password,
        String username,
        String phoneNumber,
        Set<String> roles
) {

    public static AdminUserCreateRequest from(AdminRegistrationRequest request, Set<String> roles) {
        return new AdminUserCreateRequest(
                request.loginId(),
                request.email(),
                request.password(),
                request.username(),
                request.phoneNumber(),
                roles
        );
    }
}
