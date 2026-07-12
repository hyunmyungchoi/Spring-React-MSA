package com.springmsa.memberstockservice.toss.market;

import com.springmsa.common.web.error.ApiException;
import com.springmsa.memberstockservice.market.domain.Candle;
import com.springmsa.memberstockservice.market.domain.CandleInterval;
import com.springmsa.memberstockservice.market.domain.MarketQuote;
import com.springmsa.memberstockservice.market.domain.StockSummary;
import com.springmsa.memberstockservice.toss.auth.TossAccessTokenProvider;
import com.springmsa.memberstockservice.toss.market.dto.TossApiResponse;
import com.springmsa.memberstockservice.toss.market.dto.TossCandle;
import com.springmsa.memberstockservice.toss.market.dto.TossCandlePageResponse;
import com.springmsa.memberstockservice.toss.market.dto.TossPriceResponse;
import com.springmsa.memberstockservice.toss.market.dto.TossStockInfo;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TossMarketDataAdapterTest {

    private TossAccessTokenProvider tokenProvider;
    private TossMarketDataFeignClient feignClient;
    private TossMarketDataAdapter adapter;

    @BeforeEach
    void setUp() {
        tokenProvider = mock(TossAccessTokenProvider.class);
        feignClient = mock(TossMarketDataFeignClient.class);
        adapter = new TossMarketDataAdapter(
                new FeignTossMarketDataClient(
                        feignClient,
                        tokenProvider,
                        new TossErrorResponseParser(JsonMapper.builder().findAndAddModules().build())
                )
        );
    }

    @Test
    void mapsPriceResponseUsingOfficialStringDecimalFields() {
        when(tokenProvider.getAccessToken()).thenReturn("dummy-access-token");
        when(feignClient.getPrices("Bearer dummy-access-token", "005930"))
                .thenReturn(new TossApiResponse<>(List.of(new TossPriceResponse(
                        "005930",
                        "2026-03-25T09:30:00.123+09:00",
                        "72000",
                        "KRW"
                ))));

        assertThat(adapter.getPrices(Set.of("005930")))
                .containsExactly(new MarketQuote(
                        "005930",
                        new BigDecimal("72000"),
                        "KRW",
                        OffsetDateTime.parse("2026-03-25T09:30:00.123+09:00")
                ));
    }

    @Test
    void mapsNullablePriceTimestampFromOfficialSchema() {
        when(tokenProvider.getAccessToken()).thenReturn("dummy-access-token");
        when(feignClient.getPrices("Bearer dummy-access-token", "005930"))
                .thenReturn(new TossApiResponse<>(List.of(new TossPriceResponse(
                        "005930",
                        null,
                        "72000",
                        "KRW"
                ))));

        assertThat(adapter.getPrices(Set.of("005930")))
                .containsExactly(new MarketQuote(
                        "005930",
                        new BigDecimal("72000"),
                        "KRW",
                        null
                ));
    }

    @Test
    void mapsStockResponseUsingOfficialFieldNames() {
        when(tokenProvider.getAccessToken()).thenReturn("dummy-access-token");
        when(feignClient.getStocks("Bearer dummy-access-token", "005930"))
                .thenReturn(new TossApiResponse<>(List.of(new TossStockInfo(
                        "005930",
                        "삼성전자",
                        "SamsungElec",
                        "KOSPI",
                        "KRW",
                        "ACTIVE"
                ))));

        assertThat(adapter.getStocks(Set.of("005930")))
                .containsExactly(new StockSummary(
                        "005930",
                        "삼성전자",
                        "SamsungElec",
                        "KOSPI",
                        "KRW",
                        "ACTIVE"
                ));
    }

    @Test
    void mapsCandleResponseUsingOfficialQueryParameters() {
        when(tokenProvider.getAccessToken()).thenReturn("dummy-access-token");
        when(feignClient.getCandles("Bearer dummy-access-token", "005930", "1d", 2))
                .thenReturn(new TossApiResponse<>(new TossCandlePageResponse(
                        List.of(
                                new TossCandle(
                                        "2026-03-25T09:00:00+09:00",
                                        "71600",
                                        "72300",
                                        "71500",
                                        "72000",
                                        "3521000",
                                        "KRW"
                                ),
                                new TossCandle(
                                        "2026-03-24T09:00:00+09:00",
                                        "71200",
                                        "71800",
                                        "71000",
                                        "71600",
                                        "2984000",
                                        "KRW"
                                )
                        ),
                        OffsetDateTime.parse("2026-03-24T09:00:00+09:00")
                )));

        assertThat(adapter.getCandles("005930", CandleInterval.DAY_1, 2))
                .containsExactly(
                        new Candle(
                                OffsetDateTime.parse("2026-03-25T09:00:00+09:00"),
                                new BigDecimal("71600"),
                                new BigDecimal("72300"),
                                new BigDecimal("71500"),
                                new BigDecimal("72000"),
                                new BigDecimal("3521000"),
                                "KRW"
                        ),
                        new Candle(
                                OffsetDateTime.parse("2026-03-24T09:00:00+09:00"),
                                new BigDecimal("71200"),
                                new BigDecimal("71800"),
                                new BigDecimal("71000"),
                                new BigDecimal("71600"),
                                new BigDecimal("2984000"),
                                "KRW"
                        )
                );
    }

    @Test
    void retriesOnceAfterUnauthorizedByEvictingCachedToken() {
        FeignException unauthorized = feignException(HttpStatus.UNAUTHORIZED, "{}", Map.of());
        when(tokenProvider.getAccessToken()).thenReturn("expired-token", "fresh-token");
        when(feignClient.getPrices("Bearer expired-token", "005930")).thenThrow(unauthorized);
        when(feignClient.getPrices("Bearer fresh-token", "005930"))
                .thenReturn(new TossApiResponse<>(List.of(new TossPriceResponse(
                        "005930",
                        "2026-03-25T09:30:00+09:00",
                        "72000",
                        "KRW"
                ))));

        assertThat(adapter.getPrices(Set.of("005930")))
                .extracting(MarketQuote::symbol)
                .containsExactly("005930");

        verify(tokenProvider).evictAccessToken();
    }

    @Test
    void mapsStockNotFoundFrom404Response() {
        FeignException stockNotFound = feignException(
                HttpStatus.NOT_FOUND,
                """
                        {
                          "error": {
                            "code": "stock-not-found",
                            "message": "stock not found"
                          }
                        }
                        """,
                Map.of("X-Request-Id", List.of("req-404"))
        );
        when(tokenProvider.getAccessToken()).thenReturn("dummy-access-token");
        when(feignClient.getStocks("Bearer dummy-access-token", "UNKNOWN")).thenThrow(stockNotFound);

        assertThatThrownBy(() -> adapter.getStocks(Set.of("UNKNOWN")))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.code()).isEqualTo("STOCK_NOT_FOUND");
                    assertThat(apiException.status().value()).isEqualTo(404);
                    assertThat(apiException).hasMessageContaining("req-404");
                });
    }

    @Test
    void mapsRateLimitAndParsesRetryAfterSeconds() {
        FeignException rateLimited = feignException(
                HttpStatus.TOO_MANY_REQUESTS,
                """
                        {
                          "code": "rate-limited",
                          "message": "too many requests"
                        }
                        """,
                Map.of(
                        HttpHeaders.RETRY_AFTER, List.of("7"),
                        "X-Request-Id", List.of("req-429")
                )
        );
        when(tokenProvider.getAccessToken()).thenReturn("dummy-access-token");
        when(feignClient.getPrices("Bearer dummy-access-token", "005930")).thenThrow(rateLimited);

        assertThatThrownBy(() -> adapter.getPrices(Set.of("005930")))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.code()).isEqualTo("TOSS_RATE_LIMITED");
                    assertThat(apiException.status().value()).isEqualTo(429);
                    assertThat(apiException).hasMessageContaining("7s");
                    assertThat(apiException).hasMessageContaining("req-429");
                });
    }

    @Test
    void mapsServerErrorsToMarketUnavailable() {
        FeignException serverError = feignException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                """
                        {
                          "code": "server-error",
                          "message": "unexpected"
                        }
                        """,
                Map.of("X-Request-Id", List.of("req-500"))
        );
        when(tokenProvider.getAccessToken()).thenReturn("dummy-access-token");
        when(feignClient.getPrices("Bearer dummy-access-token", "005930")).thenThrow(serverError);

        assertThatThrownBy(() -> adapter.getPrices(Set.of("005930")))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.code()).isEqualTo("TOSS_MARKET_UNAVAILABLE");
                    assertThat(apiException.status().value()).isEqualTo(503);
                    assertThat(apiException).hasMessageContaining("req-500");
                });
    }

    @Test
    void mapsTimeoutToMarketUnavailable() {
        FeignException timeout = feignException(-1, "", Map.of());
        when(tokenProvider.getAccessToken()).thenReturn("dummy-access-token");
        when(feignClient.getPrices("Bearer dummy-access-token", "005930")).thenThrow(timeout);

        assertThatThrownBy(() -> adapter.getPrices(Set.of("005930")))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.code()).isEqualTo("TOSS_MARKET_UNAVAILABLE");
                    assertThat(apiException.status().value()).isEqualTo(503);
                });
    }

    private static FeignException feignException(
            HttpStatus status,
            String body,
            Map<String, Collection<String>> headers
    ) {
        return feignException(status.value(), body, headers);
    }

    private static FeignException feignException(
            int status,
            String body,
            Map<String, Collection<String>> headers
    ) {
        FeignException exception = mock(FeignException.class);
        when(exception.status()).thenReturn(status);
        when(exception.contentUTF8()).thenReturn(body);
        when(exception.responseHeaders()).thenReturn(headers);
        return exception;
    }
}
