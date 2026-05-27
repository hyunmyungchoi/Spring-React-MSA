package com.springmsa.authserver.otp;

import com.springmsa.authserver.otp.common.OtpCodeService;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class WhatsAppOtpService {

    private static final String WHATSAPP_OTP_KEY_PREFIX = "auth:otp:whatsapp:";
    private static final Duration WHATSAPP_OTP_TTL = Duration.ofMinutes(3);

    private final OtpCodeService otpCodeService;

    public WhatsAppOtpService(OtpCodeService otpCodeService) {
        this.otpCodeService = otpCodeService;
    }

    public String issueOtp(String whatsappNumber) {
        return otpCodeService.issueOtp(buildKey(whatsappNumber), WHATSAPP_OTP_TTL);
    }

    public boolean verifyOtp(String whatsappNumber, String otp) {
        return otpCodeService.verifyOtp(buildKey(whatsappNumber), otp);
    }

    private String buildKey(String whatsappNumber) {
        return WHATSAPP_OTP_KEY_PREFIX + whatsappNumber;
    }
}