package com.springmsa.authserver.otp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OtpVerifyResponse(
        boolean verified,
        boolean authenticated,
        String redirectUrl,
        OtpUserResponse user
) {
}