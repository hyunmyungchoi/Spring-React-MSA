package com.springmsa.authserver.otp;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service
public class WhatsAppOtpService {

    private static final Duration OTP_TTL = Duration.ofMinutes(3);
    private static final String OTP_KEY_PREFIX = "auth:otp:whatsapp:";

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public WhatsAppOtpService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String issueOtp(String whatsappNumber) {
        String normalizedNumber = normalizeWhatsappNumber(whatsappNumber);
        String otp = generateSixDigitOtp();

        redisTemplate.opsForValue().set(
                buildOtpKey(normalizedNumber),
                otp,
                OTP_TTL
        );

        return otp;
    }

    public boolean verifyOtp(String whatsappNumber, String otp) {
        String normalizedNumber = normalizeWhatsappNumber(whatsappNumber);
        String key = buildOtpKey(normalizedNumber);

        String savedOtp = redisTemplate.opsForValue().get(key);

        if (savedOtp == null) {
            return false;
        }

        boolean matched = savedOtp.equals(otp);

        if (matched) {
            redisTemplate.delete(key);
        }

        return matched;
    }

    private String generateSixDigitOtp() {
        int number = secureRandom.nextInt(1_000_000);
        return String.format("%06d", number);
    }

    private String buildOtpKey(String whatsappNumber) {
        return OTP_KEY_PREFIX + whatsappNumber;
    }

    private String normalizeWhatsappNumber(String whatsappNumber) {
        if (whatsappNumber == null || whatsappNumber.isBlank()) {
            throw new IllegalArgumentException("WhatsApp number is required");
        }

        return whatsappNumber.trim();
    }
}