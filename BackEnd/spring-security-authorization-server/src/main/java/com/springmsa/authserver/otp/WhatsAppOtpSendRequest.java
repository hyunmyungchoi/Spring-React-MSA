package com.springmsa.authserver.otp;

public record WhatsAppOtpSendRequest(
        String whatsappNumber
) {
}