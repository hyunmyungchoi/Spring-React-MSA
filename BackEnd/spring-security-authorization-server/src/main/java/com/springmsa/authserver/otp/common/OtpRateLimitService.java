package com.springmsa.authserver.otp.common;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class OtpRateLimitService {

    private final StringRedisTemplate redisTemplate;

    public OtpRateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void checkAndMark(String cooldownKey, Duration cooldown) {
        Boolean allowed = redisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, "1", cooldown);

        if (Boolean.FALSE.equals(allowed)) {
            Long ttl = redisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS);
            long retryAfterSeconds = ttl == null || ttl < 0 ? 0 : ttl;

            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "OTP was already sent. Please try again after " + retryAfterSeconds + " seconds"
            );
        }
    }
}