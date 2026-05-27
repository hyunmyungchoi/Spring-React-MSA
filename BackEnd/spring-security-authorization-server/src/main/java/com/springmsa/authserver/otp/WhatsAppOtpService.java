package com.springmsa.authserver.otp;

import com.springmsa.authserver.otp.common.OtpCodeService;
import com.springmsa.authserver.otp.common.OtpProperties;
import com.springmsa.authserver.otp.common.OtpRateLimitService;
import com.springmsa.authserver.otp.whatsapp.WhatsAppOtpSender;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class WhatsAppOtpService {

    private static final String WHATSAPP_OTP_KEY_PREFIX = "auth:otp:whatsapp:";
    private static final String WHATSAPP_OTP_COOLDOWN_KEY_PREFIX = "auth:otp:cooldown:whatsapp:";
    private static final String WHATSAPP_OTP_FAIL_KEY_PREFIX = "auth:otp:fail:whatsapp:";

    private final OtpCodeService otpCodeService;
    private final OtpProperties otpProperties;
    private final OtpRateLimitService otpRateLimitService;
    private final WhatsAppOtpSender whatsAppOtpSender;

    public WhatsAppOtpService(
            OtpCodeService otpCodeService,
            OtpProperties otpProperties, OtpRateLimitService otpRateLimitService, WhatsAppOtpSender whatsAppOtpSender
    ) {
        this.otpCodeService = otpCodeService;
        this.otpProperties = otpProperties;
        this.otpRateLimitService = otpRateLimitService;
        this.whatsAppOtpSender = whatsAppOtpSender;
    }

    public String issueOtp(String whatsappNumber) {
        String normalizedWhatsappNumber = whatsappNumber.trim();

        otpRateLimitService.checkAndMark(
                buildCooldownKey(normalizedWhatsappNumber),
                Duration.ofSeconds(otpProperties.getWhatsapp().getResendCooldownSeconds())
        );

        String otp = otpCodeService.issueOtp(
                buildKey(normalizedWhatsappNumber),
                Duration.ofSeconds(otpProperties.getWhatsapp().getTtlSeconds())
        );

        whatsAppOtpSender.sendOtp(normalizedWhatsappNumber, otp);

        return otp;
    }

    private String buildCooldownKey(String whatsappNumber) {
        return WHATSAPP_OTP_COOLDOWN_KEY_PREFIX + whatsappNumber.trim();
    }

    public boolean verifyOtp(String whatsappNumber, String otp) {
        Duration ttl = Duration.ofSeconds(otpProperties.getWhatsapp().getTtlSeconds());

        return otpCodeService.verifyOtp(
                buildKey(whatsappNumber),
                buildFailKey(whatsappNumber),
                otp,
                otpProperties.getWhatsapp().getMaxVerifyAttempts(),
                ttl
        );
    }

    private String buildFailKey(String whatsappNumber) {
        return WHATSAPP_OTP_FAIL_KEY_PREFIX + whatsappNumber.trim();
    }

    public long getExpiresInSeconds() {
        return otpProperties.getWhatsapp().getTtlSeconds();
    }

    private String buildKey(String whatsappNumber) {
        return WHATSAPP_OTP_KEY_PREFIX + whatsappNumber.trim();
    }
}