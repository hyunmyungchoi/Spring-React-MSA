package com.springmsa.memberbff.stockbff.service;

import com.springmsa.common.web.error.ApiException;
import com.springmsa.memberbff.auth.service.BffOAuth2ClientService;
import com.springmsa.memberbff.common.error.DownstreamFeignExceptionMapper;
import com.springmsa.memberbff.stockbff.client.StockServiceClient;
import com.springmsa.memberbff.stockbff.dto.StockWatchItemRequest;
import com.springmsa.memberbff.stockbff.dto.StockWatchItemResponse;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StockBffService {

    private static final String STOCK_SERVICE_REQUEST_FAILED = "STOCK_SERVICE_REQUEST_FAILED";

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

    private ApiException stockFailure(FeignException exception, String fallbackMessage) {
        return DownstreamFeignExceptionMapper.toApiException(
                exception,
                STOCK_SERVICE_REQUEST_FAILED,
                fallbackMessage,
                "Stock service is unavailable"
        );
    }
}
