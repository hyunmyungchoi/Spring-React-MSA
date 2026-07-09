package com.springmsa.adminbff.registration.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminRegistrationRequest(
        @NotBlank(message = "Login ID is required")
        @Size(max = 50, message = "Login ID must be 50 characters or less")
        String loginId,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 4, max = 100, message = "Password must be 4 to 100 characters")
        String password,

        @NotBlank(message = "Username is required")
        @Size(max = 100, message = "Username must be 100 characters or less")
        String username,

        @Size(max = 30, message = "Phone number must be 30 characters or less")
        String phoneNumber
) {
}
