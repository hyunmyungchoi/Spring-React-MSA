package com.springmsa.memberstockservice.toss.auth;

import com.springmsa.common.web.error.ApiException;
import com.springmsa.memberstockservice.toss.config.TossApiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class TossAccessTokenProvider {

    private static final String LOCK_VALUE = "locked";
    private static final Duration REFRESH_LOCK_TTL = Duration.ofSeconds(5);
    private static final Duration LOCK_WAIT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration LOCK_RETRY_INTERVAL = Duration.ofMillis(50);
    private static final long TOKEN_TTL_BUFFER_SECONDS = 60;

    private final StringRedisTemplate redisTemplate;
    private final TossTokenClient tokenClient;
    private final TossApiProperties properties;
    private Sleeper sleeper = Thread::sleep;
    private Duration lockWaitTimeout = LOCK_WAIT_TIMEOUT;
    private Duration retryInterval = LOCK_RETRY_INTERVAL;

    TossAccessTokenProvider withTimingForTest(
            Sleeper sleeper,
            Duration lockWaitTimeout,
            Duration retryInterval
    ) {
        this.sleeper = sleeper;
        this.lockWaitTimeout = lockWaitTimeout;
        this.retryInterval = retryInterval;
        return this;
    }

    public String getAccessToken() {
        ValueOperations<String, String> valueOperations = redisTemplate.opsForValue();
        String cachedToken = valueOperations.get(properties.tokenCacheKey());

        if (hasText(cachedToken)) {
            return cachedToken;
        }

        Boolean lockAcquired = valueOperations.setIfAbsent(
                properties.refreshLockKey(),
                LOCK_VALUE,
                REFRESH_LOCK_TTL
        );

        if (Boolean.TRUE.equals(lockAcquired)) {
            try {
                return issueAndCacheToken(valueOperations);
            } finally {
                redisTemplate.delete(properties.refreshLockKey());
            }
        }

        return waitForToken(valueOperations);
    }

    public void evictAccessToken() {
        redisTemplate.delete(properties.tokenCacheKey());
    }

    private String issueAndCacheToken(ValueOperations<String, String> valueOperations) {
        String cachedToken = valueOperations.get(properties.tokenCacheKey());

        if (hasText(cachedToken)) {
            return cachedToken;
        }

        TossTokenResponse response = tokenClient.issueToken();
        Duration cacheTtl = getCacheTtl(response);
        valueOperations.set(
                properties.tokenCacheKey(),
                response.accessToken(),
                cacheTtl
        );
        return response.accessToken();
    }

    private Duration getCacheTtl(TossTokenResponse response) {
        if (response == null || !hasText(response.accessToken()) || !hasText(response.tokenType())) {
            throw new ApiException(TossErrorCode.TOSS_TOKEN_UNAVAILABLE);
        }

        if (!"Bearer".equalsIgnoreCase(response.tokenType())) {
            throw new ApiException(TossErrorCode.TOSS_TOKEN_UNAVAILABLE);
        }

        long cacheSeconds = response.expiresIn() - TOKEN_TTL_BUFFER_SECONDS;
        if (cacheSeconds <= 0) {
            throw new ApiException(TossErrorCode.TOSS_TOKEN_UNAVAILABLE);
        }

        return Duration.ofSeconds(cacheSeconds);
    }

    private String waitForToken(ValueOperations<String, String> valueOperations) {
        long deadline = System.nanoTime() + lockWaitTimeout.toNanos();

        while (System.nanoTime() <= deadline) {
            String cachedToken = valueOperations.get(properties.tokenCacheKey());

            if (hasText(cachedToken)) {
                return cachedToken;
            }

            pause(retryInterval);
        }

        throw new ApiException(TossErrorCode.TOSS_TOKEN_UNAVAILABLE);
    }

    private void pause(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return;
        }

        try {
            sleeper.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(TossErrorCode.TOSS_TOKEN_UNAVAILABLE, exception);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
