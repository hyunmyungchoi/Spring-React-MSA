package com.springmsa.authserver.login.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordLoginRequest(
        @NotBlank(message = "Login ID is required")
        String loginId,

        @NotBlank(message = "Password is required")
        String password
) {
}
