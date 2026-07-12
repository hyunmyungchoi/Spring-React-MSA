package com.springmsa.memberstockservice.market.service;

import com.springmsa.common.web.error.ApiException;
import com.springmsa.memberstockservice.market.cache.MarketDataCacheRepository;
import com.springmsa.memberstockservice.market.domain.Candle;
import com.springmsa.memberstockservice.market.domain.CandleInterval;
import com.springmsa.memberstockservice.market.domain.MarketQuote;
import com.springmsa.memberstockservice.market.domain.StockSummary;
import com.springmsa.memberstockservice.market.dto.CandleResponse;
import com.springmsa.memberstockservice.market.dto.DataStatus;
import com.springmsa.memberstockservice.market.dto.MarketQuoteResponse;
import com.springmsa.memberstockservice.market.dto.StockSummaryResponse;
import com.springmsa.memberstockservice.toss.auth.TossErrorCode;
import com.springmsa.memberstockservice.toss.market.TossMarketDataAdapter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MarketDataServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-12T10:15:30Z");

    private final TossMarketDataAdapter adapter = mock(TossMarketDataAdapter.class);
    private final MarketDataCacheRepository cache = mock(MarketDataCacheRepository.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private MarketDataService service;

    @BeforeEach
    void setUp() {
        service = new MarketDataService(adapter, cache, meterRegistry, clock);
    }

    @Test
    void springContextCreatesServiceWithoutAClockBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(TossMarketDataAdapter.class, () -> adapter);
            context.registerBean(MarketDataCacheRepository.class, () -> cache);
            context.registerBean(SimpleMeterRegistry.class, () -> meterRegistry);
            context.register(MarketDataService.class);

            assertThatCode(context::refresh).doesNotThrowAnyException();
            assertThat(context.getBean(MarketDataService.class)).isNotNull();
        }
    }

    @Test
    void returnsFreshQuoteFromCacheWithoutCallingToss() {
        MarketQuoteResponse cached = quote("005930", "72000", DataStatus.FRESH, NOW.minusSeconds(1));
        when(cache.findFreshQuote("005930")).thenReturn(Optional.of(cached));

        assertThat(service.getPrices(symbols("005930"))).containsExactly(cached);

        verifyNoInteractions(adapter);
        assertCounter("stock.cache.hits", "prices", "fresh", 1.0);
    }

    @Test
    void fetchesMissingQuotesAndCachesFreshResponses() {
        when(cache.findFreshQuote("005930")).thenReturn(Optional.empty());
        when(cache.tryAcquireQuoteRefreshLock("005930")).thenReturn(true);
        when(adapter.getPrices(symbols("005930"))).thenReturn(List.of(new MarketQuote(
                "005930",
                new BigDecimal("72000.50"),
                "KRW",
                OffsetDateTime.parse("2026-07-12T19:15:00+09:00")
        )));

        MarketQuoteResponse expected = quote("005930", "72000.50", DataStatus.FRESH, NOW);

        assertThat(service.getPrices(symbols("005930"))).containsExactly(expected);

        verify(cache).saveQuote(expected);
        assertCounter("stock.toss.requests", "prices", "success", 1.0);
        assertTimer("stock.toss.duration", "prices", "success", 1);
    }

    @Test
    void returnsStaleQuoteWhenTossIsUnavailable() {
        MarketQuoteResponse stale = quote("005930", "62000", DataStatus.STALE, NOW.minusSeconds(20));
        when(cache.findFreshQuote("005930")).thenReturn(Optional.empty());
        when(cache.tryAcquireQuoteRefreshLock("005930")).thenReturn(true);
        when(adapter.getPrices(symbols("005930"))).thenThrow(new ApiException(TossErrorCode.TOSS_MARKET_UNAVAILABLE));
        when(cache.findStaleQuote("005930")).thenReturn(Optional.of(stale));

        assertThat(service.getPrices(symbols("005930"))).containsExactly(stale);

        verify(cache, never()).saveQuote(stale);
        assertCounter("stock.toss.requests", "prices", "error", 1.0);
        assertCounter("stock.toss.errors", "prices", "error", 1.0);
        assertCounter("stock.cache.stale_served", "prices", "stale", 1.0);
        assertTimer("stock.toss.duration", "prices", "error", 1);
    }

    @Test
    void lockLoserUsesStaleQuoteWithoutCallingToss() {
        MarketQuoteResponse stale = quote("005930", "62000", DataStatus.STALE, NOW.minusSeconds(20));
        when(cache.findFreshQuote("005930")).thenReturn(Optional.empty());
        when(cache.tryAcquireQuoteRefreshLock("005930")).thenReturn(false);
        when(cache.findStaleQuote("005930")).thenReturn(Optional.of(stale));

        assertThat(service.getPrices(symbols("005930"))).containsExactly(stale);

        verifyNoInteractions(adapter);
        assertCounter("stock.cache.stale_served", "prices", "stale", 1.0);
    }

    @Test
    void rethrowsStockNotFoundWithoutServingStaleData() {
        when(cache.findFreshQuote("UNKNOWN")).thenReturn(Optional.empty());
        when(cache.tryAcquireQuoteRefreshLock("UNKNOWN")).thenReturn(true);
        when(adapter.getPrices(symbols("UNKNOWN"))).thenThrow(new ApiException(TossErrorCode.STOCK_NOT_FOUND));

        assertThatThrownBy(() -> service.getPrices(symbols("UNKNOWN")))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).code()).isEqualTo("STOCK_NOT_FOUND"));

        verify(cache, never()).findStaleQuote("UNKNOWN");
    }

    @Test
    void returnsCachedStockInfoAndFetchesMissingSymbols() {
        StockSummaryResponse cached = stock("005930", DataStatus.FRESH, NOW.minusSeconds(10));
        StockSummaryResponse fetched = stock("AAPL", DataStatus.FRESH, NOW);
        when(cache.findStock("005930")).thenReturn(Optional.of(cached));
        when(cache.findStock("AAPL")).thenReturn(Optional.empty());
        when(adapter.getStocks(symbols("AAPL"))).thenReturn(List.of(new StockSummary(
                "AAPL",
                "Apple",
                "Apple Inc.",
                "NASDAQ",
                "USD",
                "ACTIVE"
        )));

        assertThat(service.getStocks(symbols("005930", "AAPL"))).containsExactly(cached, fetched);

        verify(cache).saveStock(fetched);
        assertCounter("stock.cache.hits", "stocks", "fresh", 1.0);
        assertCounter("stock.toss.requests", "stocks", "success", 1.0);
    }

    @Test
    void returnsCachedCandlesAndFetchesMissingCandleSeries() {
        CandleResponse cached = candle("72000", DataStatus.FRESH, NOW.minusSeconds(5));
        CandleResponse fetched = candle("72100", DataStatus.FRESH, NOW);
        when(cache.findCandles("005930", CandleInterval.MINUTE_1, 100)).thenReturn(Optional.of(List.of(cached)));
        when(cache.findCandles("005930", CandleInterval.DAY_1, 2)).thenReturn(Optional.empty());
        when(adapter.getCandles("005930", CandleInterval.DAY_1, 2)).thenReturn(List.of(new Candle(
                OffsetDateTime.parse("2026-07-12T09:00:00+09:00"),
                new BigDecimal("71900"),
                new BigDecimal("72300"),
                new BigDecimal("71800"),
                new BigDecimal("72100"),
                new BigDecimal("2500"),
                "KRW"
        )));

        assertThat(service.getCandles("005930", CandleInterval.MINUTE_1, 100)).containsExactly(cached);
        assertThat(service.getCandles("005930", CandleInterval.DAY_1, 2)).containsExactly(fetched);

        verify(cache).saveCandles("005930", CandleInterval.DAY_1, 2, List.of(fetched));
        assertCounter("stock.cache.hits", "candles", "fresh", 1.0);
        assertCounter("stock.toss.requests", "candles", "success", 1.0);
    }

    @Test
    void incrementsRateLimitedCounterWhenServingStaleQuoteAfterRateLimit() {
        MarketQuoteResponse stale = quote("005930", "62000", DataStatus.STALE, NOW.minusSeconds(20));
        when(cache.findFreshQuote("005930")).thenReturn(Optional.empty());
        when(cache.tryAcquireQuoteRefreshLock("005930")).thenReturn(true);
        when(adapter.getPrices(symbols("005930"))).thenThrow(new ApiException(TossErrorCode.TOSS_RATE_LIMITED));
        when(cache.findStaleQuote("005930")).thenReturn(Optional.of(stale));

        assertThat(service.getPrices(symbols("005930"))).containsExactly(stale);

        assertCounter("stock.toss.rate_limited", "prices", "error", 1.0);
        assertCounter("stock.cache.stale_served", "prices", "stale", 1.0);
    }

    private static LinkedHashSet<String> symbols(String... symbols) {
        return new LinkedHashSet<>(List.of(symbols));
    }

    private static MarketQuoteResponse quote(String symbol, String lastPrice, DataStatus status, Instant fetchedAt) {
        return new MarketQuoteResponse(
                symbol,
                lastPrice,
                "KRW",
                "2026-07-12T19:15+09:00",
                fetchedAt,
                status
        );
    }

    private static StockSummaryResponse stock(String symbol, DataStatus status, Instant fetchedAt) {
        return new StockSummaryResponse(
                symbol,
                symbol.equals("005930") ? "Samsung Electronics" : "Apple",
                symbol.equals("005930") ? "Samsung Electronics" : "Apple Inc.",
                symbol.equals("005930") ? "KOSPI" : "NASDAQ",
                symbol.equals("005930") ? "KRW" : "USD",
                "ACTIVE",
                fetchedAt,
                status
        );
    }

    private static CandleResponse candle(String closePrice, DataStatus status, Instant fetchedAt) {
        return new CandleResponse(
                "2026-07-12T09:00+09:00",
                "71900",
                "72300",
                "71800",
                closePrice,
                "2500",
                "KRW",
                fetchedAt,
                status
        );
    }

    private void assertCounter(String name, String endpoint, String outcome, double expectedCount) {
        assertThat(meterRegistry.get(name)
                .tag("endpoint", endpoint)
                .tag("outcome", outcome)
                .counter()
                .count()).isEqualTo(expectedCount);
    }

    private void assertTimer(String name, String endpoint, String outcome, long expectedCount) {
        assertThat(meterRegistry.get(name)
                .tag("endpoint", endpoint)
                .tag("outcome", outcome)
                .timer()
                .count()).isEqualTo(expectedCount);
    }
}
