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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

@Service
public class MarketDataService {

    private static final String PRICES_ENDPOINT = "prices";
    private static final String STOCKS_ENDPOINT = "stocks";
    private static final String CANDLES_ENDPOINT = "candles";

    private final TossMarketDataAdapter adapter;
    private final MarketDataCacheRepository cache;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    @Autowired
    public MarketDataService(
            TossMarketDataAdapter adapter,
            MarketDataCacheRepository cache,
            MeterRegistry meterRegistry
    ) {
        this(adapter, cache, meterRegistry, Clock.systemUTC());
    }

    MarketDataService(
            TossMarketDataAdapter adapter,
            MarketDataCacheRepository cache,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        this.adapter = adapter;
        this.cache = cache;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public List<MarketQuoteResponse> getPrices(Set<String> symbols) {
        Map<String, MarketQuoteResponse> responses = new LinkedHashMap<>();
        LinkedHashSet<String> misses = new LinkedHashSet<>();

        for (String symbol : symbols) {
            Optional<MarketQuoteResponse> cached = cache.findFreshQuote(symbol);
            if (cached.isPresent()) {
                responses.put(symbol, cached.get());
                increment("stock.cache.hits", PRICES_ENDPOINT, "fresh");
            } else {
                misses.add(symbol);
            }
        }

        if (!misses.isEmpty()) {
            LinkedHashSet<String> locked = new LinkedHashSet<>();
            LinkedHashSet<String> fetchMisses = new LinkedHashSet<>();

            try {
                for (String symbol : misses) {
                    if (cache.tryAcquireQuoteRefreshLock(symbol)) {
                        locked.add(symbol);
                        Optional<MarketQuoteResponse> cachedAfterLock = cache.findFreshQuote(symbol);
                        if (cachedAfterLock.isPresent()) {
                            responses.put(symbol, cachedAfterLock.get());
                            increment("stock.cache.hits", PRICES_ENDPOINT, "fresh");
                        } else {
                            fetchMisses.add(symbol);
                        }
                    } else {
                        responses.put(symbol, quoteFromConcurrentRefresh(symbol));
                    }
                }

                if (!fetchMisses.isEmpty()) {
                    try {
                        List<MarketQuoteResponse> fetched = callToss(
                                PRICES_ENDPOINT,
                                () -> adapter.getPrices(fetchMisses).stream()
                                        .map(this::toResponse)
                                        .toList()
                        );
                        for (MarketQuoteResponse response : fetched) {
                            cache.saveQuote(response);
                            responses.put(response.symbol(), response);
                        }
                    } catch (ApiException exception) {
                        if (!isRecoverableMarketFailure(exception)) {
                            throw exception;
                        }

                        for (String symbol : fetchMisses) {
                            MarketQuoteResponse stale = cache.findStaleQuote(symbol)
                                    .orElseThrow(() -> exception);
                            responses.put(symbol, stale);
                            increment("stock.cache.stale_served", PRICES_ENDPOINT, "stale");
                        }
                    }
                }
            } finally {
                for (String symbol : locked) {
                    cache.releaseQuoteRefreshLock(symbol);
                }
            }
        }

        return orderedResponses(symbols, responses);
    }

    public List<StockSummaryResponse> getStocks(Set<String> symbols) {
        Map<String, StockSummaryResponse> responses = new LinkedHashMap<>();
        LinkedHashSet<String> misses = new LinkedHashSet<>();

        for (String symbol : symbols) {
            Optional<StockSummaryResponse> cached = cache.findStock(symbol);
            if (cached.isPresent()) {
                responses.put(symbol, cached.get());
                increment("stock.cache.hits", STOCKS_ENDPOINT, "fresh");
            } else {
                misses.add(symbol);
            }
        }

        if (!misses.isEmpty()) {
            List<StockSummaryResponse> fetched = callToss(
                    STOCKS_ENDPOINT,
                    () -> adapter.getStocks(misses).stream()
                            .map(this::toResponse)
                            .toList()
            );
            for (StockSummaryResponse response : fetched) {
                cache.saveStock(response);
                responses.put(response.symbol(), response);
            }
        }

        return orderedResponses(symbols, responses);
    }

    public List<CandleResponse> getCandles(String symbol, CandleInterval interval, int count) {
        Optional<List<CandleResponse>> cached = cache.findCandles(symbol, interval, count);
        if (cached.isPresent()) {
            increment("stock.cache.hits", CANDLES_ENDPOINT, "fresh");
            return cached.get();
        }

        List<CandleResponse> fetched = callToss(
                CANDLES_ENDPOINT,
                () -> adapter.getCandles(symbol, interval, count).stream()
                        .map(this::toResponse)
                        .toList()
        );
        cache.saveCandles(symbol, interval, count, fetched);
        return fetched;
    }

    private MarketQuoteResponse toResponse(MarketQuote quote) {
        return new MarketQuoteResponse(
                quote.symbol(),
                decimal(quote.lastPrice()),
                quote.currency(),
                timestamp(quote.timestamp()),
                Instant.now(clock),
                DataStatus.FRESH
        );
    }

    private StockSummaryResponse toResponse(StockSummary stock) {
        return new StockSummaryResponse(
                stock.symbol(),
                stock.name(),
                stock.englishName(),
                stock.market(),
                stock.currency(),
                stock.status(),
                Instant.now(clock),
                DataStatus.FRESH
        );
    }

    private CandleResponse toResponse(Candle candle) {
        return new CandleResponse(
                timestamp(candle.timestamp()),
                decimal(candle.openPrice()),
                decimal(candle.highPrice()),
                decimal(candle.lowPrice()),
                decimal(candle.closePrice()),
                decimal(candle.volume()),
                candle.currency(),
                Instant.now(clock),
                DataStatus.FRESH
        );
    }

    private String decimal(BigDecimal value) {
        return value.toPlainString();
    }

    private String timestamp(java.time.OffsetDateTime value) {
        return value == null ? null : value.toString();
    }

    private <T> List<T> orderedResponses(Set<String> symbols, Map<String, T> responses) {
        List<T> ordered = new ArrayList<>();
        for (String symbol : symbols) {
            T response = responses.get(symbol);
            if (response != null) {
                ordered.add(response);
            }
        }
        return ordered;
    }

    private <T> T callToss(String endpoint, Supplier<T> supplier) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            T result = supplier.get();
            increment("stock.toss.requests", endpoint, "success");
            sample.stop(timer(endpoint, "success"));
            return result;
        } catch (RuntimeException exception) {
            increment("stock.toss.requests", endpoint, "error");
            increment("stock.toss.errors", endpoint, "error");
            if (isRateLimited(exception)) {
                increment("stock.toss.rate_limited", endpoint, "error");
            }
            sample.stop(timer(endpoint, "error"));
            throw exception;
        }
    }

    private boolean isRecoverableMarketFailure(ApiException exception) {
        return "TOSS_MARKET_UNAVAILABLE".equals(exception.code())
                || "TOSS_RATE_LIMITED".equals(exception.code());
    }

    private boolean isRateLimited(RuntimeException exception) {
        return exception instanceof ApiException apiException
                && "TOSS_RATE_LIMITED".equals(apiException.code());
    }

    private MarketQuoteResponse quoteFromConcurrentRefresh(String symbol) {
        Optional<MarketQuoteResponse> fresh = cache.findFreshQuote(symbol);
        if (fresh.isPresent()) {
            increment("stock.cache.hits", PRICES_ENDPOINT, "fresh");
            return fresh.get();
        }

        Optional<MarketQuoteResponse> stale = cache.findStaleQuote(symbol);
        if (stale.isPresent()) {
            increment("stock.cache.stale_served", PRICES_ENDPOINT, "stale");
            return stale.get();
        }

        throw new ApiException(TossErrorCode.TOSS_MARKET_UNAVAILABLE);
    }

    private void increment(String name, String endpoint, String outcome) {
        Counter.builder(name)
                .tag("endpoint", endpoint)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    private Timer timer(String endpoint, String outcome) {
        return Timer.builder("stock.toss.duration")
                .tag("endpoint", endpoint)
                .tag("outcome", outcome)
                .register(meterRegistry);
    }
}
