package com.springmsa.authserver.login;

import com.springmsa.authserver.otp.dto.OtpUserResponse;

public record PasswordLoginResponse(
        boolean authenticated,
        String redirectUrl,
        OtpUserResponse user
) {
}
