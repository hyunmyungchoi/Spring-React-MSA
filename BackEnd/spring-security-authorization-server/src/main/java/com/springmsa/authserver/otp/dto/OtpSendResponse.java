package com.springmsa.authserver.otp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OtpSendResponse(
        boolean sent,
        long expiresInSeconds,
        String devOtp
) {
    public static OtpSendResponse withoutDevOtp(long expiresInSeconds) {
        return new OtpSendResponse(true, expiresInSeconds, null);
    }

    public static OtpSendResponse withDevOtp(long expiresInSeconds, String devOtp) {
        return new OtpSendResponse(true, expiresInSeconds, devOtp);
    }
}