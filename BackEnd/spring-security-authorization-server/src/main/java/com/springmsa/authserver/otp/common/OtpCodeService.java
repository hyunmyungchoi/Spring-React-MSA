package com.springmsa.authserver.otp.common;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;

@Service
public class OtpCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;

    public OtpCodeService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String issueOtp(String key, Duration ttl) {
        String otp = generateSixDigitOtp();

        redisTemplate.opsForValue().set(key, otp, ttl);

        return otp;
    }

    public boolean verifyOtp(String key, String inputOtp) {
        String savedOtp = redisTemplate.opsForValue().get(key);

        if (savedOtp == null) {
            return false;
        }

        boolean matched = savedOtp.equals(inputOtp);

        if (matched) {
            redisTemplate.delete(key);
        }

        return matched;
    }

    public boolean verifyOtp(
            String otpKey,
            String failKey,
            String inputOtp,
            int maxAttempts,
            Duration ttl
    ) {
        String savedOtp = redisTemplate.opsForValue().get(otpKey);

        if (savedOtp == null) {
            return false;
        }

        boolean matched = savedOtp.equals(inputOtp);

        if (matched) {
            redisTemplate.delete(otpKey);
            redisTemplate.delete(failKey);
            return true;
        }

        Long failCount = redisTemplate.opsForValue().increment(failKey);

        if (failCount != null && failCount == 1L) {
            redisTemplate.expire(failKey, ttl);
        }

        if (failCount != null && failCount >= maxAttempts) {
            redisTemplate.delete(otpKey);
            redisTemplate.delete(failKey);

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "OTP verification failed too many times. Please request a new OTP."
            );
        }

        return false;
    }

    private String generateSixDigitOtp() {
        int number = RANDOM.nextInt(1_000_000);
        return String.format("%06d", number);
    }
}