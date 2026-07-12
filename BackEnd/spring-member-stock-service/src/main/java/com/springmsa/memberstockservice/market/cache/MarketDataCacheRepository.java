package com.springmsa.memberstockservice.market.cache;

import com.springmsa.memberstockservice.market.domain.CandleInterval;
import com.springmsa.memberstockservice.market.dto.CandleResponse;
import com.springmsa.memberstockservice.market.dto.DataStatus;
import com.springmsa.memberstockservice.market.dto.MarketQuoteResponse;
import com.springmsa.memberstockservice.market.dto.StockSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MarketDataCacheRepository {

    private static final Duration QUOTE_TTL = Duration.ofSeconds(2);
    private static final Duration QUOTE_STALE_TTL = Duration.ofMinutes(5);
    private static final Duration QUOTE_REFRESH_LOCK_TTL = Duration.ofSeconds(2);
    private static final Duration STOCK_TTL = Duration.ofHours(24);
    private static final Duration CANDLE_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;
    private final MarketDataCacheCodec cacheCodec;

    public Optional<MarketQuoteResponse> findFreshQuote(String symbol) {
        return read(quoteKey(symbol), MarketQuoteResponse.class);
    }

    public Optional<MarketQuoteResponse> findStaleQuote(String symbol) {
        return read(staleQuoteKey(symbol), MarketQuoteResponse.class);
    }

    public void saveQuote(MarketQuoteResponse response) {
        write(quoteKey(response.symbol()), response, QUOTE_TTL);
        write(staleQuoteKey(response.symbol()), staleCopy(response), QUOTE_STALE_TTL);
    }

    public boolean tryAcquireQuoteRefreshLock(String symbol) {
        Boolean acquired = operations().setIfAbsent(refreshLockKey(symbol), "1", QUOTE_REFRESH_LOCK_TTL);
        return Boolean.TRUE.equals(acquired);
    }

    public void releaseQuoteRefreshLock(String symbol) {
        redisTemplate.delete(refreshLockKey(symbol));
    }

    public Optional<StockSummaryResponse> findStock(String symbol) {
        return read(stockKey(symbol), StockSummaryResponse.class);
    }

    public void saveStock(StockSummaryResponse response) {
        write(stockKey(response.symbol()), response, STOCK_TTL);
    }

    public Optional<List<CandleResponse>> findCandles(String symbol, CandleInterval interval, int count) {
        return read(candlesKey(symbol, interval, count), CandleCachePayload.class)
                .map(CandleCachePayload::candles);
    }

    public void saveCandles(String symbol, CandleInterval interval, int count, List<CandleResponse> candles) {
        write(candlesKey(symbol, interval, count), new CandleCachePayload(candles), CANDLE_TTL);
    }

    private MarketQuoteResponse staleCopy(MarketQuoteResponse response) {
        return new MarketQuoteResponse(
                response.symbol(),
                response.lastPrice(),
                response.currency(),
                response.timestamp(),
                response.fetchedAt(),
                DataStatus.STALE
        );
    }

    private <T> Optional<T> read(String key, Class<T> type) {
        return cacheCodec.decode(operations().get(key), type);
    }

    private void write(String key, Object value, Duration ttl) {
        cacheCodec.encode(value)
                .ifPresent(json -> operations().set(key, json, ttl));
    }

    private ValueOperations<String, String> operations() {
        return redisTemplate.opsForValue();
    }

    private String quoteKey(String symbol) {
        return "stock:quote:" + symbol;
    }

    private String staleQuoteKey(String symbol) {
        return "stock:quote-stale:" + symbol;
    }

    private String refreshLockKey(String symbol) {
        return "stock:quote-refresh-lock:" + symbol;
    }

    private String stockKey(String symbol) {
        return "stock:info:" + symbol;
    }

    private String candlesKey(String symbol, CandleInterval interval, int count) {
        return "stock:candles:" + symbol + ":" + interval.apiValue() + ":" + count;
    }
}
