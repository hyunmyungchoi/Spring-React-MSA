package com.springmsa.memberstockservice.toss.market;

import com.springmsa.common.web.error.ApiException;
import com.springmsa.memberstockservice.market.domain.CandleInterval;
import com.springmsa.memberstockservice.toss.auth.TossAccessTokenProvider;
import com.springmsa.memberstockservice.toss.auth.TossErrorCode;
import com.springmsa.memberstockservice.toss.market.dto.TossApiResponse;
import com.springmsa.memberstockservice.toss.market.dto.TossCandle;
import com.springmsa.memberstockservice.toss.market.dto.TossCandlePageResponse;
import com.springmsa.memberstockservice.toss.market.dto.TossErrorResponse;
import com.springmsa.memberstockservice.toss.market.dto.TossPriceResponse;
import com.springmsa.memberstockservice.toss.market.dto.TossStockInfo;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
class FeignTossMarketDataClient implements TossMarketDataClient {

    private final TossMarketDataFeignClient feignClient;
    private final TossAccessTokenProvider tokenProvider;
    private final TossErrorResponseParser errorResponseParser;

    @Override
    public List<TossPriceResponse> getPrices(Set<String> symbols) {
        TossApiResponse<List<TossPriceResponse>> response = getWithSingleUnauthorizedRetry(
                () -> feignClient.getPrices(bearerToken(), joinSymbols(symbols))
        );
        return response.result();
    }

    @Override
    public List<TossStockInfo> getStocks(Set<String> symbols) {
        TossApiResponse<List<TossStockInfo>> response = getWithSingleUnauthorizedRetry(
                () -> feignClient.getStocks(bearerToken(), joinSymbols(symbols))
        );
        return response.result();
    }

    @Override
    public List<TossCandle> getCandles(String symbol, CandleInterval interval, int count) {
        TossApiResponse<TossCandlePageResponse> response = getWithSingleUnauthorizedRetry(
                () -> feignClient.getCandles(bearerToken(), symbol, interval.apiValue(), count)
        );
        return response.result().candles();
    }

    private String bearerToken() {
        return "Bearer " + tokenProvider.getAccessToken();
    }

    private <T> T getWithSingleUnauthorizedRetry(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (FeignException exception) {
            if (exception.status() == HttpStatus.UNAUTHORIZED.value()) {
                tokenProvider.evictAccessToken();
                try {
                    return supplier.get();
                } catch (FeignException retryException) {
                    throw mapException(retryException);
                }
            }

            throw mapException(exception);
        }
    }

    private ApiException mapException(FeignException exception) {
        HttpStatus status = HttpStatus.resolve(exception.status());
        TossErrorResponse errorResponse = errorResponseParser.parse(exception.contentUTF8());
        String requestId = firstHeader(exception.responseHeaders(), "X-Request-Id");

        if (status == HttpStatus.NOT_FOUND && errorCodeEquals(errorResponse, "stock-not-found")) {
            return new ApiException(
                    HttpStatus.NOT_FOUND,
                    TossErrorCode.STOCK_NOT_FOUND.code(),
                    formatMessage("Stock not found in Toss market data", requestId, null),
                    exception
            );
        }

        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            Duration retryAfter = parseRetryAfter(exception.responseHeaders());
            return new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    TossErrorCode.TOSS_RATE_LIMITED.code(),
                    formatMessage("Toss market data rate limited", requestId, retryAfter),
                    exception
            );
        }

        if (status != null && status.is5xxServerError()) {
            return new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    TossErrorCode.TOSS_MARKET_UNAVAILABLE.code(),
                    formatMessage("Toss market data unavailable", requestId, null),
                    exception
            );
        }

        return new ApiException(TossErrorCode.TOSS_MARKET_UNAVAILABLE, exception);
    }

    private boolean errorCodeEquals(TossErrorResponse response, String expectedCode) {
        return response.code() != null && response.code().equalsIgnoreCase(expectedCode);
    }

    private Duration parseRetryAfter(Map<String, Collection<String>> headers) {
        String retryAfter = firstHeader(headers, HttpHeaders.RETRY_AFTER);
        if (retryAfter == null || retryAfter.isBlank()) {
            return null;
        }

        try {
            return Duration.ofSeconds(Long.parseLong(retryAfter));
        } catch (NumberFormatException ignored) {
            try {
                return Duration.between(OffsetDateTime.now(), ZonedDateTime.parse(retryAfter).toOffsetDateTime());
            } catch (RuntimeException ignoredAgain) {
                return null;
            }
        }
    }

    private String firstHeader(Map<String, Collection<String>> headers, String name) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }

        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .filter(values -> values != null && !values.isEmpty())
                .flatMap(Collection::stream)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String formatMessage(String baseMessage, String requestId, Duration retryAfter) {
        StringBuilder builder = new StringBuilder(baseMessage);

        if (retryAfter != null && !retryAfter.isNegative()) {
            builder.append(" retryAfter=").append(retryAfter.getSeconds()).append('s');
        }

        if (requestId != null && !requestId.isBlank()) {
            builder.append(" requestId=").append(requestId);
        }

        return builder.toString();
    }

    private String joinSymbols(Set<String> symbols) {
        return symbols.stream()
                .sorted(Comparator.naturalOrder())
                .reduce((left, right) -> left + "," + right)
                .orElseThrow(() -> new IllegalArgumentException("symbols must not be empty"));
    }
}
