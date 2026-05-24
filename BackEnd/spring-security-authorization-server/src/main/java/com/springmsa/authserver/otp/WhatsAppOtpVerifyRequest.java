package com.springmsa.authserver.otp;

public record WhatsAppOtpVerifyRequest(
        String whatsappNumber,
        String otp
) {
}