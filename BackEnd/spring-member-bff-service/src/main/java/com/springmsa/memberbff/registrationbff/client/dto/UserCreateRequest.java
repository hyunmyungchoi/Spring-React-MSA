package com.springmsa.memberbff.registrationbff.client.dto;

import com.springmsa.memberbff.registrationbff.dto.RegistrationRequest;

import java.util.Set;

public record UserCreateRequest(
        String loginId,
        String email,
        String password,
        String username,
        String phoneNumber,
        String whatsappNumber,
        Set<String> roles
) {

    public static UserCreateRequest from(RegistrationRequest request, Set<String> roles) {
        return new UserCreateRequest(
                request.loginId(),
                request.email(),
                request.password(),
                request.username(),
                request.phoneNumber(),
                request.whatsappNumber(),
                roles
        );
    }
}
