package com.springmsa.memberbff.stockbff.service;

import com.springmsa.common.web.error.ApiException;
import com.springmsa.memberbff.auth.service.BffOAuth2ClientService;
import com.springmsa.memberbff.common.error.DownstreamFeignExceptionMapper;
import com.springmsa.memberbff.stockbff.client.StockServiceClient;
import com.springmsa.memberbff.stockbff.dto.MarketQuoteResponse;
import com.springmsa.memberbff.stockbff.dto.MarketWorkspaceResponse;
import com.springmsa.memberbff.stockbff.dto.PartialFailure;
import com.springmsa.memberbff.stockbff.dto.StockSummaryResponse;
import com.springmsa.memberbff.stockbff.dto.StockWatchItemRequest;
import com.springmsa.memberbff.stockbff.dto.StockWatchItemResponse;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StockBffService {

    private static final String STOCK_SERVICE_REQUEST_FAILED = "STOCK_SERVICE_REQUEST_FAILED";
    private static final List<String> TRACE_ID_HEADERS = List.of(
            "X-Trace-Id",
            "X-Request-Id",
            "X-Correlation-Id",
            "X-B3-TraceId",
            "traceparent"
    );

    private final BffOAuth2ClientService bffOAuth2ClientService;
    private final StockServiceClient stockServiceClient;

    public Map<String, Object> getCurrentStockUser(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        try {
            return stockServiceClient.me(bearerToken(authentication, request, response));
        } catch (FeignException exception) {
            throw stockFailure(exception, "Stock user request failed");
        }
    }

    public List<StockWatchItemResponse> findWatchItems(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        try {
            return stockServiceClient.findWatchItems(bearerToken(authentication, request, response));
        } catch (FeignException exception) {
            throw stockFailure(exception, "Stock watch items request failed");
        }
    }

    public MarketWorkspaceResponse getMarketWorkspace(Authentication authentication, HttpServletRequest request, HttpServletResponse response, String symbols) {
        String authorization = bearerToken(authentication, request, response);
        List<PartialFailure> failures = new ArrayList<>();

        List<StockSummaryResponse> stocks = findMarketStocks(authorization, symbols, failures);
        List<MarketQuoteResponse> prices = findMarketPrices(authorization, symbols, failures);
        List<StockWatchItemResponse> watchItems = findWatchItems(authorization, failures);

        return new MarketWorkspaceResponse(stocks, prices, watchItems, failures);
    }

    public StockWatchItemResponse createWatchItem(Authentication authentication, HttpServletRequest request, HttpServletResponse response, StockWatchItemRequest watchItemRequest) {
        try {
            return stockServiceClient.createWatchItem(bearerToken(authentication, request, response), watchItemRequest);
        } catch (FeignException exception) {
            throw stockFailure(exception, "Stock watch item creation failed");
        }
    }

    public StockWatchItemResponse updateWatchItem(Authentication authentication, HttpServletRequest request, HttpServletResponse response, Long itemId, StockWatchItemRequest watchItemRequest) {
        try {
            return stockServiceClient.updateWatchItem(bearerToken(authentication, request, response), itemId, watchItemRequest);
        } catch (FeignException exception) {
            throw stockFailure(exception, "Stock watch item update failed");
        }
    }

    public void deleteWatchItem(Authentication authentication, HttpServletRequest request, HttpServletResponse response, Long itemId) {
        try {
            stockServiceClient.deleteWatchItem(bearerToken(authentication, request, response), itemId);
        } catch (FeignException exception) {
            throw stockFailure(exception, "Stock watch item deletion failed");
        }
    }

    private String bearerToken(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return "Bearer " + bffOAuth2ClientService.getAccessToken(authentication, request, response);
    }

    private List<StockSummaryResponse> findMarketStocks(String authorization, String symbols, List<PartialFailure> failures) {
        try {
            return stockServiceClient.findMarketStocks(authorization, symbols);
        } catch (FeignException exception) {
            failures.add(partialFailure("stocks", exception, "Stock market stocks request failed"));
            return List.of();
        }
    }

    private List<MarketQuoteResponse> findMarketPrices(String authorization, String symbols, List<PartialFailure> failures) {
        try {
            return stockServiceClient.findMarketPrices(authorization, symbols);
        } catch (FeignException exception) {
            failures.add(partialFailure("prices", exception, "Stock market prices request failed"));
            return List.of();
        }
    }

    private List<StockWatchItemResponse> findWatchItems(String authorization, List<PartialFailure> failures) {
        try {
            return stockServiceClient.findWatchItems(authorization);
        } catch (FeignException exception) {
            failures.add(partialFailure("watchItems", exception, "Stock watch items request failed"));
            return List.of();
        }
    }

    private PartialFailure partialFailure(String component, FeignException exception, String fallbackMessage) {
        ApiException mapped = stockFailure(exception, fallbackMessage);
        return new PartialFailure(component, mapped.code(), mapped.getMessage(), traceId(exception));
    }

    private String traceId(FeignException exception) {
        Map<String, Collection<String>> headers = exception.responseHeaders();

        if (headers == null || headers.isEmpty()) {
            return null;
        }

        for (String traceIdHeader : TRACE_ID_HEADERS) {
            String traceId = headerValue(headers, traceIdHeader);
            if (traceId != null) {
                return traceId;
            }
        }

        return null;
    }

    private String headerValue(Map<String, Collection<String>> headers, String headerName) {
        for (Map.Entry<String, Collection<String>> header : headers.entrySet()) {
            if (!header.getKey().equalsIgnoreCase(headerName)) {
                continue;
            }

            for (String value : header.getValue()) {
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }

        return null;
    }

    private ApiException stockFailure(FeignException exception, String fallbackMessage) {
        return DownstreamFeignExceptionMapper.toApiException(
                exception,
                STOCK_SERVICE_REQUEST_FAILED,
                fallbackMessage,
                "Stock service is unavailable"
        );
    }
}
