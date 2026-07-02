package com.springmsa.adminbff.registration.dto;

public record AdminRegistrationRequest(
        String loginId,
        String email,
        String password,
        String username,
        String phoneNumber
) {
}
