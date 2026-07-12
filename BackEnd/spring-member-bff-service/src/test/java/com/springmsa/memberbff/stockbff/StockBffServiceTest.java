package com.springmsa.memberbff.stockbff;

import com.springmsa.memberbff.auth.service.BffOAuth2ClientService;
import com.springmsa.memberbff.stockbff.client.StockServiceClient;
import com.springmsa.memberbff.stockbff.dto.DataStatus;
import com.springmsa.memberbff.stockbff.dto.MarketQuoteResponse;
import com.springmsa.memberbff.stockbff.dto.MarketWorkspaceResponse;
import com.springmsa.memberbff.stockbff.dto.PartialFailure;
import com.springmsa.memberbff.stockbff.dto.StockSummaryResponse;
import com.springmsa.memberbff.stockbff.dto.StockWatchItemResponse;
import com.springmsa.memberbff.stockbff.service.StockBffService;
import feign.FeignException;
import feign.Request;
import feign.Response;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockBffServiceTest {

    private static final Instant FETCHED_AT = Instant.parse("2026-07-12T10:15:30Z");

    private final BffOAuth2ClientService bffOAuth2ClientService = mock(BffOAuth2ClientService.class);
    private final StockServiceClient stockServiceClient = mock(StockServiceClient.class);
    private final Authentication authentication = mock(Authentication.class);
    private final HttpServletRequest request = new MockHttpServletRequest();
    private final HttpServletResponse response = new MockHttpServletResponse();

    private StockBffService service;

    @BeforeEach
    void setUp() {
        service = new StockBffService(bffOAuth2ClientService, stockServiceClient);
    }

    @Test
    void marketWorkspaceAggregatesStocksPricesAndWatchItems() {
        String symbols = "005930,AAPL";
        List<StockSummaryResponse> stocks = List.of(stock("005930"), stock("AAPL"));
        List<MarketQuoteResponse> prices = List.of(price("005930"), price("AAPL"));
        List<StockWatchItemResponse> watchItems = List.of(watchItem(1L, "005930", "Samsung"));

        when(bffOAuth2ClientService.getAccessToken(authentication, request, response)).thenReturn("access-token");
        when(stockServiceClient.findMarketStocks("Bearer access-token", symbols)).thenReturn(stocks);
        when(stockServiceClient.findMarketPrices("Bearer access-token", symbols)).thenReturn(prices);
        when(stockServiceClient.findWatchItems("Bearer access-token")).thenReturn(watchItems);

        MarketWorkspaceResponse workspace = service.getMarketWorkspace(authentication, request, response, symbols);

        assertThat(workspace.stocks()).containsExactlyElementsOf(stocks);
        assertThat(workspace.prices()).containsExactlyElementsOf(prices);
        assertThat(workspace.watchItems()).containsExactlyElementsOf(watchItems);
        assertThat(workspace.failures()).isEmpty();
        verify(stockServiceClient).findMarketStocks("Bearer access-token", symbols);
        verify(stockServiceClient).findMarketPrices("Bearer access-token", symbols);
        verify(stockServiceClient).findWatchItems("Bearer access-token");
    }

    @Test
    void marketWorkspaceRecordsPartialFailureAndPreservesSuccessfulComponents() {
        String symbols = "005930,AAPL";
        List<StockSummaryResponse> stocks = List.of(stock("005930"), stock("AAPL"));
        List<StockWatchItemResponse> watchItems = List.of(watchItem(1L, "005930", "Samsung"));

        when(bffOAuth2ClientService.getAccessToken(authentication, request, response)).thenReturn("access-token");
        when(stockServiceClient.findMarketStocks("Bearer access-token", symbols)).thenReturn(stocks);
        when(stockServiceClient.findMarketPrices("Bearer access-token", symbols))
                .thenThrow(downstreamException("PRICE_UNAVAILABLE", "Price source unavailable", "trace-price-1"));
        when(stockServiceClient.findWatchItems("Bearer access-token")).thenReturn(watchItems);

        MarketWorkspaceResponse workspace = service.getMarketWorkspace(authentication, request, response, symbols);

        assertThat(workspace.stocks()).containsExactlyElementsOf(stocks);
        assertThat(workspace.prices()).isEmpty();
        assertThat(workspace.watchItems()).containsExactlyElementsOf(watchItems);
        assertThat(workspace.failures()).containsExactly(new PartialFailure(
                "prices",
                "PRICE_UNAVAILABLE",
                "Price source unavailable",
                "trace-price-1"
        ));
        verify(stockServiceClient).findMarketStocks("Bearer access-token", symbols);
        verify(stockServiceClient).findMarketPrices("Bearer access-token", symbols);
        verify(stockServiceClient).findWatchItems("Bearer access-token");
    }

    private static StockSummaryResponse stock(String symbol) {
        return new StockSummaryResponse(
                symbol,
                symbol.equals("005930") ? "Samsung Electronics" : "Apple",
                symbol.equals("005930") ? "Samsung Electronics" : "Apple Inc.",
                symbol.equals("005930") ? "KOSPI" : "NASDAQ",
                symbol.equals("005930") ? "KRW" : "USD",
                "ACTIVE",
                FETCHED_AT,
                DataStatus.FRESH
        );
    }

    private static MarketQuoteResponse price(String symbol) {
        return new MarketQuoteResponse(
                symbol,
                symbol.equals("005930") ? "72000" : "210.15",
                symbol.equals("005930") ? "KRW" : "USD",
                "2026-07-12T19:15+09:00",
                FETCHED_AT,
                DataStatus.FRESH
        );
    }

    private static StockWatchItemResponse watchItem(Long id, String symbol, String memo) {
        return new StockWatchItemResponse(id, symbol, memo, "user-a", FETCHED_AT, FETCHED_AT);
    }

    private static FeignException downstreamException(String code, String message, String traceId) {
        String body = """
                {"code":"%s","message":"%s","errors":[]}
                """.formatted(code, message);
        Request request = Request.create(
                Request.HttpMethod.GET,
                "/api/stock/market/prices",
                Map.of(),
                new byte[0],
                StandardCharsets.UTF_8,
                null
        );
        Map<String, Collection<String>> headers = Map.of("X-Trace-Id", List.of(traceId));
        Response response = Response.builder()
                .status(503)
                .reason("Service Unavailable")
                .headers(headers)
                .body(body, StandardCharsets.UTF_8)
                .request(request)
                .build();
        return FeignException.errorStatus("prices", response);
    }
}
