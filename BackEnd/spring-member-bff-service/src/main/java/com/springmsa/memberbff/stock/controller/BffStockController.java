package com.springmsa.memberbff.stock.controller;

import com.springmsa.common.web.response.MsaResponse;
import com.springmsa.memberbff.stock.dto.StockWatchItemRequest;
import com.springmsa.memberbff.stock.dto.StockWatchItemResponse;
import com.springmsa.memberbff.stock.service.StockService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class BffStockController {

    private final StockService stockService;

    @GetMapping("/stock/me")
    public ResponseEntity<MsaResponse<Map<String, Object>>> me(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(MsaResponse.ok(stockService.getCurrentStockUser(authentication, request, response)));
    }

    @GetMapping("/stock/watch-items")
    public ResponseEntity<MsaResponse<List<StockWatchItemResponse>>> watchItems(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(MsaResponse.ok(stockService.findWatchItems(authentication, request, response)));
    }

    @PostMapping("/stock/watch-items")
    public ResponseEntity<MsaResponse<StockWatchItemResponse>> createWatchItem(Authentication authentication, HttpServletRequest request, HttpServletResponse response, @RequestBody StockWatchItemRequest watchItemRequest) {
        return ResponseEntity.ok(MsaResponse.ok(stockService.createWatchItem(authentication, request, response, watchItemRequest)));
    }

    @PutMapping("/stock/watch-items/{itemId}")
    public ResponseEntity<MsaResponse<StockWatchItemResponse>> updateWatchItem(Authentication authentication, HttpServletRequest request, HttpServletResponse response, @PathVariable Long itemId, @RequestBody StockWatchItemRequest watchItemRequest) {
        return ResponseEntity.ok(MsaResponse.ok(stockService.updateWatchItem(authentication, request, response, itemId, watchItemRequest)));
    }

    @DeleteMapping("/stock/watch-items/{itemId}")
    public ResponseEntity<MsaResponse<Void>> deleteWatchItem(Authentication authentication, HttpServletRequest request, HttpServletResponse response, @PathVariable Long itemId) {
        stockService.deleteWatchItem(authentication, request, response, itemId);
        return ResponseEntity.ok(MsaResponse.ok(null));
    }
}
