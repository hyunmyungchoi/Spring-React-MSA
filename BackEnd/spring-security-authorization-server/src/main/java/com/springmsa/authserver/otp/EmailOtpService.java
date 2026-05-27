package com.springmsa.authserver.otp;

import com.springmsa.authserver.otp.common.OtpCodeService;
import com.springmsa.authserver.otp.common.OtpProperties;
import com.springmsa.authserver.otp.common.OtpRateLimitService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

@Service
public class EmailOtpService {

    private static final String EMAIL_OTP_KEY_PREFIX = "auth:otp:email:";
    private static final String EMAIL_OTP_COOLDOWN_KEY_PREFIX = "auth:otp:cooldown:email:";
    private static final String EMAIL_OTP_FAIL_KEY_PREFIX = "auth:otp:fail:email:";

    private final OtpCodeService otpCodeService;
    private final OtpProperties otpProperties;
    private final OtpRateLimitService otpRateLimitService;

    public EmailOtpService(
            OtpCodeService otpCodeService,
            OtpProperties otpProperties, OtpRateLimitService otpRateLimitService
    ) {
        this.otpCodeService = otpCodeService;
        this.otpProperties = otpProperties;
        this.otpRateLimitService = otpRateLimitService;
    }

    public String issueOtp(String email) {
        otpRateLimitService.checkAndMark(
                buildCooldownKey(email),
                Duration.ofSeconds(otpProperties.getEmail().getResendCooldownSeconds())
        );

        return otpCodeService.issueOtp(
                buildKey(email),
                Duration.ofSeconds(otpProperties.getEmail().getTtlSeconds())
        );
    }

    private String buildCooldownKey(String email) {
        return EMAIL_OTP_COOLDOWN_KEY_PREFIX + normalizeEmail(email);
    }

    public boolean verifyOtp(String email, String otp) {
        Duration ttl = Duration.ofSeconds(otpProperties.getEmail().getTtlSeconds());

        return otpCodeService.verifyOtp(
                buildKey(email),
                buildFailKey(email),
                otp,
                otpProperties.getEmail().getMaxVerifyAttempts(),
                ttl
        );
    }

    private String buildFailKey(String email) {
        return EMAIL_OTP_FAIL_KEY_PREFIX + normalizeEmail(email);
    }

    public long getExpiresInSeconds() {
        return otpProperties.getEmail().getTtlSeconds();
    }

    private String buildKey(String email) {
        return EMAIL_OTP_KEY_PREFIX + normalizeEmail(email);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}