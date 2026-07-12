package com.springmsa.memberstockservice.market.cache;

import com.springmsa.memberstockservice.market.domain.CandleInterval;
import com.springmsa.memberstockservice.market.dto.CandleResponse;
import com.springmsa.memberstockservice.market.dto.DataStatus;
import com.springmsa.memberstockservice.market.dto.MarketQuoteResponse;
import com.springmsa.memberstockservice.market.dto.StockSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketDataCacheRepositoryTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = (ValueOperations<String, String>) mock(ValueOperations.class);

    private MarketDataCacheRepository repository;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        repository = new MarketDataCacheRepository(
                redisTemplate,
                JsonMapper.builder().findAndAddModules().build()
        );
    }

    @Test
    void saveQuoteWritesFreshAndStaleKeysWithRequiredTtls() {
        MarketQuoteResponse quote = quote(DataStatus.FRESH);

        repository.saveQuote(quote);

        verify(valueOperations).set(eq("stock:quote:005930"), anyString(), eq(Duration.ofSeconds(2)));
        verify(valueOperations).set(eq("stock:quote-stale:005930"), anyString(), eq(Duration.ofMinutes(5)));
    }

    @Test
    void findStaleQuoteDoesNotExtendTheStaleTtl() throws Exception {
        MarketQuoteResponse quote = quote(DataStatus.STALE);
        String json = JsonMapper.builder().findAndAddModules().build().writeValueAsString(quote);
        when(valueOperations.get("stock:quote-stale:005930")).thenReturn(json);

        assertThat(repository.findStaleQuote("005930")).contains(quote);

        verify(valueOperations, never()).set(eq("stock:quote-stale:005930"), anyString(), eq(Duration.ofMinutes(5)));
    }

    @Test
    void saveStockUsesTwentyFourHourTtl() {
        StockSummaryResponse stock = new StockSummaryResponse(
                "005930",
                "Samsung Electronics",
                "Samsung Electronics",
                "KOSPI",
                "KRW",
                "ACTIVE",
                Instant.parse("2026-07-12T10:15:30Z"),
                DataStatus.FRESH
        );

        repository.saveStock(stock);

        verify(valueOperations).set(eq("stock:info:005930"), anyString(), eq(Duration.ofHours(24)));
    }

    @Test
    void saveCandlesUsesCompositeKeyAndThirtySecondTtl() {
        CandleResponse candle = candle();

        repository.saveCandles("005930", CandleInterval.MINUTE_1, 100, List.of(candle));

        verify(valueOperations).set(
                eq("stock:candles:005930:1m:100"),
                anyString(),
                eq(Duration.ofSeconds(30))
        );
    }

    private static MarketQuoteResponse quote(DataStatus status) {
        return new MarketQuoteResponse(
                "005930",
                "72000",
                "KRW",
                "2026-07-12T19:15+09:00",
                Instant.parse("2026-07-12T10:15:30Z"),
                status
        );
    }

    private static CandleResponse candle() {
        return new CandleResponse(
                "2026-07-12T09:00+09:00",
                "71900",
                "72300",
                "71800",
                "72100",
                "2500",
                "KRW",
                Instant.parse("2026-07-12T10:15:30Z"),
                DataStatus.FRESH
        );
    }
}
