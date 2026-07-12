package com.springmsa.memberstockservice.toss.market;

import com.springmsa.common.web.error.ApiException;
import com.springmsa.memberstockservice.market.domain.CandleInterval;
import com.springmsa.memberstockservice.toss.auth.TossAccessTokenProvider;
import com.springmsa.memberstockservice.toss.auth.TossErrorCode;
import com.springmsa.memberstockservice.toss.config.TossApiProperties;
import com.springmsa.memberstockservice.toss.market.dto.TossApiResponse;
import com.springmsa.memberstockservice.toss.market.dto.TossCandle;
import com.springmsa.memberstockservice.toss.market.dto.TossPriceResponse;
import com.springmsa.memberstockservice.toss.market.dto.TossStockInfo;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public interface TossMarketDataClient {

    List<TossPriceResponse> getPrices(Set<String> symbols);

    List<TossStockInfo> getStocks(Set<String> symbols);

    List<TossCandle> getCandles(String symbol, CandleInterval interval, int count);
}

@Component
class RestClientTossMarketDataClient implements TossMarketDataClient {

    private static final ParameterizedTypeReference<TossApiResponse<List<TossPriceResponse>>> PRICE_RESPONSE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<TossApiResponse<List<TossStockInfo>>> STOCK_RESPONSE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<TossApiResponse<TossCandlePageResponse>> CANDLE_RESPONSE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final TossAccessTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;

    RestClientTossMarketDataClient(
            RestClient.Builder builder,
            TossAccessTokenProvider tokenProvider,
            TossApiProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
        this.tokenProvider = tokenProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<TossPriceResponse> getPrices(Set<String> symbols) {
        TossApiResponse<List<TossPriceResponse>> response = getWithSingleUnauthorizedRetry(
                () -> restClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/api/v1/prices")
                                .queryParam("symbols", joinSymbols(symbols))
                                .build())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .retrieve()
                        .body(PRICE_RESPONSE)
        );
        return response.result();
    }

    @Override
    public List<TossStockInfo> getStocks(Set<String> symbols) {
        TossApiResponse<List<TossStockInfo>> response = getWithSingleUnauthorizedRetry(
                () -> restClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/api/v1/stocks")
                                .queryParam("symbols", joinSymbols(symbols))
                                .build())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .retrieve()
                        .body(STOCK_RESPONSE)
        );
        return response.result();
    }

    @Override
    public List<TossCandle> getCandles(String symbol, CandleInterval interval, int count) {
        TossApiResponse<TossCandlePageResponse> response = getWithSingleUnauthorizedRetry(
                () -> restClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/api/v1/candles")
                                .queryParam("symbol", symbol)
                                .queryParam("interval", interval.apiValue())
                                .queryParam("count", count)
                                .build())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .retrieve()
                        .body(CANDLE_RESPONSE)
        );
        return response.result().candles();
    }

    private String bearerToken() {
        return "Bearer " + tokenProvider.getAccessToken();
    }

    private <T> T getWithSingleUnauthorizedRetry(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                tokenProvider.evictAccessToken();
                try {
                    return supplier.get();
                } catch (RestClientException retryException) {
                    throw mapException(retryException);
                }
            }

            throw mapException(exception);
        } catch (RestClientException exception) {
            throw mapException(exception);
        }
    }

    private ApiException mapException(RestClientException exception) {
        if (exception instanceof RestClientResponseException responseException) {
            HttpStatus status = HttpStatus.resolve(responseException.getStatusCode().value());
            TossErrorResponse errorResponse = parseErrorResponse(responseException);
            String requestId = responseException.getResponseHeaders() == null
                    ? null
                    : responseException.getResponseHeaders().getFirst("X-Request-Id");

            if (status == HttpStatus.NOT_FOUND && errorCodeEquals(errorResponse, "stock-not-found")) {
                return new ApiException(
                        HttpStatus.NOT_FOUND,
                        TossErrorCode.STOCK_NOT_FOUND.code(),
                        formatMessage("Stock not found in Toss market data", requestId, null),
                        responseException
                );
            }

            if (status == HttpStatus.TOO_MANY_REQUESTS) {
                Duration retryAfter = parseRetryAfter(responseException.getResponseHeaders());
                return new ApiException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        TossErrorCode.TOSS_RATE_LIMITED.code(),
                        formatMessage("Toss market data rate limited", requestId, retryAfter),
                        responseException
                );
            }

            if (status != null && status.is5xxServerError()) {
                return new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        TossErrorCode.TOSS_MARKET_UNAVAILABLE.code(),
                        formatMessage("Toss market data unavailable", requestId, null),
                        responseException
                );
            }
        }

        return new ApiException(TossErrorCode.TOSS_MARKET_UNAVAILABLE, exception);
    }

    private TossErrorResponse parseErrorResponse(RestClientResponseException exception) {
        byte[] body = exception.getResponseBodyAsByteArray();

        if (body == null || body.length == 0) {
            return new TossErrorResponse(null, null);
        }

        try {
            TossErrorEnvelope envelope = objectMapper.readValue(body, TossErrorEnvelope.class);
            if (envelope.error() != null) {
                return envelope.error();
            }

            return objectMapper.readValue(body, TossErrorResponse.class);
        } catch (RuntimeException ignored) {
            return new TossErrorResponse(null, null);
        }
    }

    private boolean errorCodeEquals(TossErrorResponse response, String expectedCode) {
        return response.code() != null && response.code().equalsIgnoreCase(expectedCode);
    }

    private Duration parseRetryAfter(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }

        String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
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

record TossCandlePageResponse(
        List<TossCandle> candles,
        OffsetDateTime nextBefore
) {
}

record TossErrorResponse(
        String code,
        String message
) {
}

record TossErrorEnvelope(
        TossErrorResponse error
) {
}
