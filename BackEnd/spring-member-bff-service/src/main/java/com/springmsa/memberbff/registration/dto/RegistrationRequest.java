package com.springmsa.memberbff.registration.dto;

public record RegistrationRequest(
        String loginId,
        String email,
        String password,
        String username,
        String phoneNumber,
        String whatsappNumber
) {
}
