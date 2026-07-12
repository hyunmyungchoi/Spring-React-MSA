package com.springmsa.memberstockservice.toss.auth;

import com.springmsa.common.web.error.ApiException;
import com.springmsa.memberstockservice.toss.config.TossApiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TossAccessTokenProviderTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = (ValueOperations<String, String>) mock(ValueOperations.class);
    private final TossTokenClient tokenClient = mock(TossTokenClient.class);

    private TossApiProperties properties;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        properties = new TossApiProperties(
                "https://openapi.tossinvest.com",
                "dummy-client-id",
                "dummy-client-secret",
                "toss:oauth:access-token",
                "toss:oauth:refresh-lock"
        );
    }

    @Test
    void reusesCachedTokenUntilRefreshWindow() {
        TossAccessTokenProvider provider = new TossAccessTokenProvider(
                redisTemplate,
                tokenClient,
                properties,
                duration -> {
                },
                Duration.ofSeconds(1),
                Duration.ofMillis(10)
        );
        when(valueOperations.get("toss:oauth:access-token")).thenReturn("cached-token");

        assertThat(provider.getAccessToken()).isEqualTo("cached-token");

        verifyNoInteractions(tokenClient);
    }

    @Test
    void issuesAndCachesTokenWhenRedisIsEmpty() {
        TossAccessTokenProvider provider = new TossAccessTokenProvider(
                redisTemplate,
                tokenClient,
                properties,
                duration -> {
                },
                Duration.ofSeconds(1),
                Duration.ofMillis(10)
        );
        when(valueOperations.get("toss:oauth:access-token")).thenReturn((String) null, (String) null);
        when(valueOperations.setIfAbsent("toss:oauth:refresh-lock", "locked", Duration.ofSeconds(5))).thenReturn(true);
        when(tokenClient.issueToken()).thenReturn(new TossTokenResponse("issued-token", "Bearer", 3600));

        assertThat(provider.getAccessToken()).isEqualTo("issued-token");

        verify(valueOperations).set("toss:oauth:access-token", "issued-token", Duration.ofSeconds(3540));
    }

    @Test
    void rejectsTokenResponseThatCannotProducePositiveCacheTtl() {
        TossAccessTokenProvider provider = new TossAccessTokenProvider(
                redisTemplate,
                tokenClient,
                properties,
                duration -> {
                },
                Duration.ofSeconds(1),
                Duration.ofMillis(10)
        );
        when(valueOperations.get("toss:oauth:access-token")).thenReturn((String) null, (String) null);
        when(valueOperations.setIfAbsent("toss:oauth:refresh-lock", "locked", Duration.ofSeconds(5))).thenReturn(true);
        when(tokenClient.issueToken()).thenReturn(new TossTokenResponse("issued-token", "Bearer", 60));

        assertThatThrownBy(provider::getAccessToken)
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.code()).isEqualTo("TOSS_TOKEN_UNAVAILABLE");
                    assertThat(apiException.status().value()).isEqualTo(503);
                });

        verify(valueOperations, never()).set(eq("toss:oauth:access-token"), any(), any(Duration.class));
    }

    @Test
    void rejectsNonBearerTokenTypeBeforeCaching() {
        TossAccessTokenProvider provider = new TossAccessTokenProvider(
                redisTemplate,
                tokenClient,
                properties,
                duration -> {
                },
                Duration.ofSeconds(1),
                Duration.ofMillis(10)
        );
        when(valueOperations.get("toss:oauth:access-token")).thenReturn((String) null, (String) null);
        when(valueOperations.setIfAbsent("toss:oauth:refresh-lock", "locked", Duration.ofSeconds(5))).thenReturn(true);
        when(tokenClient.issueToken()).thenReturn(new TossTokenResponse("issued-token", "MAC", 3600));

        assertThatThrownBy(provider::getAccessToken)
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.code()).isEqualTo("TOSS_TOKEN_UNAVAILABLE");
                    assertThat(apiException.status().value()).isEqualTo(503);
                });

        verify(valueOperations, never()).set(eq("toss:oauth:access-token"), any(), any(Duration.class));
    }

    @Test
    void waitsForAnotherNodeToPopulateTokenWhenRefreshLockIsBusy() {
        TossAccessTokenProvider provider = new TossAccessTokenProvider(
                redisTemplate,
                tokenClient,
                properties,
                duration -> {
                },
                Duration.ofSeconds(1),
                Duration.ofMillis(10)
        );
        when(valueOperations.get("toss:oauth:access-token")).thenReturn(null, null, "cached-after-wait");
        when(valueOperations.setIfAbsent("toss:oauth:refresh-lock", "locked", Duration.ofSeconds(5))).thenReturn(false);

        assertThat(provider.getAccessToken()).isEqualTo("cached-after-wait");

        verifyNoInteractions(tokenClient);
        verify(valueOperations, never()).set(eq("toss:oauth:access-token"), any(), any(Duration.class));
    }

    @Test
    void evictsCachedTokenOnDemand() {
        TossAccessTokenProvider provider = new TossAccessTokenProvider(
                redisTemplate,
                tokenClient,
                properties,
                duration -> {
                },
                Duration.ZERO,
                Duration.ZERO
        );

        provider.evictAccessToken();

        verify(redisTemplate).delete("toss:oauth:access-token");
        verifyNoInteractions(tokenClient);
    }

    @Test
    void throwsServiceUnavailableWhenNoTokenAppearsBeforeTimeout() {
        TossAccessTokenProvider provider = new TossAccessTokenProvider(
                redisTemplate,
                tokenClient,
                properties,
                duration -> {
                },
                Duration.ZERO,
                Duration.ZERO
        );
        when(valueOperations.get("toss:oauth:access-token")).thenReturn((String) null, (String) null);
        when(valueOperations.setIfAbsent("toss:oauth:refresh-lock", "locked", Duration.ofSeconds(5))).thenReturn(false);

        assertThatThrownBy(provider::getAccessToken)
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.code()).isEqualTo("TOSS_TOKEN_UNAVAILABLE");
                    assertThat(apiException.status().value()).isEqualTo(503);
                });
    }
}
