package com.springmsa.authserver.otp.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.otp.whatsapp.sender",
        havingValue = "dev",
        matchIfMissing = true
)
public class DevWhatsAppOtpSender implements WhatsAppOtpSender {

    private static final Logger log = LoggerFactory.getLogger(DevWhatsAppOtpSender.class);

    @Override
    public void sendOtp(String whatsappNumber, String otp) {
        log.info("[DEV WhatsApp OTP] whatsappNumber={}, otp={}", whatsappNumber, otp);
    }
}