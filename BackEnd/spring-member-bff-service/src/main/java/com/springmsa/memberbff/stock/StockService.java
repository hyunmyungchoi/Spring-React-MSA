package com.springmsa.memberbff.stock;

import com.springmsa.memberbff.auth.BffOAuth2ClientService;
import com.springmsa.memberbff.client.StockApiClient;
import com.springmsa.memberbff.stock.dto.StockWatchItemRequest;
import com.springmsa.memberbff.stock.dto.StockWatchItemResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StockService {

    private final BffOAuth2ClientService bffOAuth2ClientService;
    private final StockApiClient stockApiClient;

    public Map<String, Object> getCurrentStockUser(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return stockApiClient.me(bearerToken(authentication, request, response));
    }

    public List<StockWatchItemResponse> findWatchItems(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return stockApiClient.findWatchItems(bearerToken(authentication, request, response));
    }

    public StockWatchItemResponse createWatchItem(Authentication authentication, HttpServletRequest request, HttpServletResponse response, StockWatchItemRequest watchItemRequest) {
        return stockApiClient.createWatchItem(bearerToken(authentication, request, response), watchItemRequest);
    }

    public StockWatchItemResponse updateWatchItem(Authentication authentication, HttpServletRequest request, HttpServletResponse response, Long itemId, StockWatchItemRequest watchItemRequest) {
        return stockApiClient.updateWatchItem(bearerToken(authentication, request, response), itemId, watchItemRequest);
    }

    public void deleteWatchItem(Authentication authentication, HttpServletRequest request, HttpServletResponse response, Long itemId) {
        stockApiClient.deleteWatchItem(bearerToken(authentication, request, response), itemId);
    }

    private String bearerToken(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return "Bearer " + bffOAuth2ClientService.getAccessToken(authentication, request, response);
    }
}
