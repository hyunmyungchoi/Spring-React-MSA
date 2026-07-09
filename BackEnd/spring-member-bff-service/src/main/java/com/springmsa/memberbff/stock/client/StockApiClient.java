package com.springmsa.memberbff.stock.client;

import com.springmsa.memberbff.stock.dto.StockWatchItemRequest;
import com.springmsa.memberbff.stock.dto.StockWatchItemResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.Map;

@FeignClient(name = "member-stock-api-client", url = "${bff.api.stock-api-base-url}")
public interface StockApiClient {

    @GetMapping("/api/stock/me")
    Map<String, Object> me(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    @GetMapping("/api/stock/watch-items")
    List<StockWatchItemResponse> findWatchItems(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    @PostMapping("/api/stock/watch-items")
    StockWatchItemResponse createWatchItem(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody StockWatchItemRequest request);

    @PutMapping("/api/stock/watch-items/{itemId}")
    StockWatchItemResponse updateWatchItem(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable("itemId") Long itemId, @RequestBody StockWatchItemRequest request);

    @DeleteMapping("/api/stock/watch-items/{itemId}")
    void deleteWatchItem(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable("itemId") Long itemId);
}
