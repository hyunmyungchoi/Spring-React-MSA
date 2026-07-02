package com.springmsa.authserver.login.dto;

public record PasswordLoginResponse(
        boolean authenticated,
        String redirectUrl,
        LoginUserResponse user
) {
}
