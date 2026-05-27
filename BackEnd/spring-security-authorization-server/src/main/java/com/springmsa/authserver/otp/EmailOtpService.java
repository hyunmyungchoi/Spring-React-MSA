package com.springmsa.authserver.otp;

import com.springmsa.authserver.otp.common.OtpCodeService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

@Service
public class EmailOtpService {

    private static final String EMAIL_OTP_KEY_PREFIX = "auth:otp:email:";
    private static final Duration EMAIL_OTP_TTL = Duration.ofMinutes(3);

    private final OtpCodeService otpCodeService;

    public EmailOtpService(OtpCodeService otpCodeService) {
        this.otpCodeService = otpCodeService;
    }

    public String issueOtp(String email) {
        return otpCodeService.issueOtp(buildKey(email), EMAIL_OTP_TTL);
    }

    public boolean verifyOtp(String email, String otp) {
        return otpCodeService.verifyOtp(buildKey(email), otp);
    }

    private String buildKey(String email) {
        return EMAIL_OTP_KEY_PREFIX + normalizeEmail(email);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}