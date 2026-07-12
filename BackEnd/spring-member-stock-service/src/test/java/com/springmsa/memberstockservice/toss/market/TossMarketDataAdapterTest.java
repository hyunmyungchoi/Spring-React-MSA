package com.springmsa.memberstockservice.toss.market;

import com.springmsa.common.web.error.ApiException;
import com.springmsa.memberstockservice.market.domain.Candle;
import com.springmsa.memberstockservice.market.domain.CandleInterval;
import com.springmsa.memberstockservice.market.domain.MarketQuote;
import com.springmsa.memberstockservice.market.domain.StockSummary;
import com.springmsa.memberstockservice.toss.auth.TossAccessTokenProvider;
import com.springmsa.memberstockservice.toss.config.TossApiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import tools.jackson.databind.json.JsonMapper;

class TossMarketDataAdapterTest {

    private TossAccessTokenProvider tokenProvider;
    private MockRestServiceServer server;
    private TossMarketDataAdapter adapter;

    @BeforeEach
    void setUp() {
        TossApiProperties properties = new TossApiProperties(
                "https://openapi.tossinvest.com",
                "dummy-client-id",
                "dummy-client-secret",
                "toss:oauth:access-token",
                "toss:oauth:refresh-lock"
        );
        tokenProvider = mock(TossAccessTokenProvider.class);
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.baseUrl());
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new TossMarketDataAdapter(
                new RestClientTossMarketDataClient(
                        builder,
                        tokenProvider,
                        properties,
                        JsonMapper.builder().findAndAddModules().build()
                )
        );
    }

    @Test
    void mapsPriceResponseUsingOfficialStringDecimalFields() {
        when(tokenProvider.getAccessToken()).thenReturn("dummy-access-token");

        server.expect(once(), requestTo("https://openapi.tossinvest.com/api/v1/prices?symbols=005930"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer dummy-access-token"))
                .andRespond(withSuccess("""
                        {
                          "result": [
                            {
                              "symbol": "005930",
                              "timestamp": "2026-03-25T09:30:00.123+09:00",
                              "lastPrice": "72000",
                              "currency": "KRW"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(adapter.getPrices(Set.of("005930")))
                .containsExactly(new MarketQuote(
                        "005930",
                        new BigDecimal("72000"),
                        "KRW",
                        OffsetDateTime.parse("2026-03-25T09:30:00.123+09:00")
                ));
    }

    @Test
    void mapsStockResponseUsingOfficialFieldNames() {
        when(tokenProvider.getAccessToken()).thenReturn("dummy-access-token");

        server.expect(once(), requestTo("https://openapi.tossinvest.com/api/v1/stocks?symbols=005930"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer dummy-access-token"))
                .andRespond(withSuccess("""
                        {
                          "result": [
                            {
                              "symbol": "005930",
                              "name": "삼성전자",
                              "englishName": "SamsungElec",
                              "isinCode": "KR7005930003",
                              "market": "KOSPI",
                              "securityType": "STOCK",
                              "isCommonShare": true,
                              "status": "ACTIVE",
                              "currency": "KRW",
                              "listDate": "1975-06-11",
                              "delistDate": null,
                              "sharesOutstanding": "5919637922",
                              "leverageFactor": null,
                              "koreanMarketDetail": {
                                "liquidationTrading": false,
                                "nxtSupported": true,
                                "krxTradingSuspended": false,
                                "nxtTradingSuspended": false
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

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

        server.expect(once(), requestTo("https://openapi.tossinvest.com/api/v1/candles?symbol=005930&interval=1d&count=2"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer dummy-access-token"))
                .andRespond(withSuccess("""
                        {
                          "result": {
                            "candles": [
                              {
                                "timestamp": "2026-03-25T09:00:00+09:00",
                                "openPrice": "71600",
                                "highPrice": "72300",
                                "lowPrice": "71500",
                                "closePrice": "72000",
                                "volume": "3521000",
                                "currency": "KRW"
                              },
                              {
                                "timestamp": "2026-03-24T09:00:00+09:00",
                                "openPrice": "71200",
                                "highPrice": "71800",
                                "lowPrice": "71000",
                                "closePrice": "71600",
                                "volume": "2984000",
                                "currency": "KRW"
                              }
                            ],
                            "nextBefore": "2026-03-24T09:00:00+09:00"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

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
        when(tokenProvider.getAccessToken()).thenReturn("expired-token", "fresh-token");

        server.expect(once(), requestTo("https://openapi.tossinvest.com/api/v1/prices?symbols=005930"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer expired-token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body("""
                        {
                          "code": "unauthorized",
                          "message": "token expired"
                        }
                        """));
        server.expect(once(), requestTo("https://openapi.tossinvest.com/api/v1/prices?symbols=005930"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer fresh-token"))
                .andRespond(withSuccess("""
                        {
                          "result": [
                            {
                              "symbol": "005930",
                              "timestamp": "2026-03-25T09:30:00+09:00",
                              "lastPrice": "72000",
                              "currency": "KRW"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(adapter.getPrices(Set.of("005930")))
                .extracting(MarketQuote::symbol)
                .containsExactly("005930");

        verify(tokenProvider).evictAccessToken();
    }

    @Test
    void mapsStockNotFoundFrom404Response() {
        when(tokenProvider.getAccessToken()).thenReturn("dummy-access-token");

        server.expect(once(), requestTo("https://openapi.tossinvest.com/api/v1/stocks?symbols=UNKNOWN"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-Id", "req-404")
                        .body("""
                                {
                                  "code": "stock-not-found",
                                  "message": "stock not found"
                                }
                                """));

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
        when(tokenProvider.getAccessToken()).thenReturn("dummy-access-token");

        server.expect(once(), requestTo("https://openapi.tossinvest.com/api/v1/prices?symbols=005930"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.RETRY_AFTER, "7")
                        .header("X-Request-Id", "req-429")
                        .body("""
                                {
                                  "code": "rate-limited",
                                  "message": "too many requests"
                                }
                                """));

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
        when(tokenProvider.getAccessToken()).thenReturn("dummy-access-token");

        server.expect(once(), requestTo("https://openapi.tossinvest.com/api/v1/prices?symbols=005930"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-Id", "req-500")
                        .body("""
                                {
                                  "code": "server-error",
                                  "message": "unexpected"
                                }
                                """));

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
        when(tokenProvider.getAccessToken()).thenReturn("dummy-access-token");

        server.expect(once(), requestTo("https://openapi.tossinvest.com/api/v1/prices?symbols=005930"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withException(new IOException("Read timed out")));

        assertThatThrownBy(() -> adapter.getPrices(Set.of("005930")))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.code()).isEqualTo("TOSS_MARKET_UNAVAILABLE");
                    assertThat(apiException.status().value()).isEqualTo(503);
                });
    }
}
