package com.springmsa.authserver.login;

public record PasswordLoginResponse(
        boolean authenticated,
        String redirectUrl,
        LoginUserResponse user
) {
}
