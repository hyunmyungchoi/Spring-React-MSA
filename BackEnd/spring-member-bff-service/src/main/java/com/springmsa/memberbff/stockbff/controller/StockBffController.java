package com.springmsa.memberbff.stockbff.controller;

import com.springmsa.common.web.response.MsaResponse;
import com.springmsa.memberbff.stockbff.dto.MarketWorkspaceResponse;
import com.springmsa.memberbff.stockbff.dto.StockWatchItemRequest;
import com.springmsa.memberbff.stockbff.dto.StockWatchItemResponse;
import com.springmsa.memberbff.stockbff.service.StockBffService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class StockBffController {

    private final StockBffService stockBffService;

    @GetMapping("/stock/me")
    public ResponseEntity<MsaResponse<Map<String, Object>>> me(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(MsaResponse.ok(stockBffService.getCurrentStockUser(authentication, request, response)));
    }

    @GetMapping("/stock/watch-items")
    public ResponseEntity<MsaResponse<List<StockWatchItemResponse>>> watchItems(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(MsaResponse.ok(stockBffService.findWatchItems(authentication, request, response)));
    }

    @GetMapping("/stock/market/workspace")
    public ResponseEntity<MsaResponse<MarketWorkspaceResponse>> marketWorkspace(Authentication authentication, HttpServletRequest request, HttpServletResponse response, @RequestParam(defaultValue = "") String symbols) {
        return ResponseEntity.ok(MsaResponse.ok(stockBffService.getMarketWorkspace(authentication, request, response, symbols)));
    }

    @PostMapping("/stock/watch-items")
    public ResponseEntity<MsaResponse<StockWatchItemResponse>> createWatchItem(Authentication authentication, HttpServletRequest request, HttpServletResponse response, @RequestBody StockWatchItemRequest watchItemRequest) {
        return ResponseEntity.ok(MsaResponse.ok(stockBffService.createWatchItem(authentication, request, response, watchItemRequest)));
    }

    @PutMapping("/stock/watch-items/{itemId}")
    public ResponseEntity<MsaResponse<StockWatchItemResponse>> updateWatchItem(Authentication authentication, HttpServletRequest request, HttpServletResponse response, @PathVariable Long itemId, @RequestBody StockWatchItemRequest watchItemRequest) {
        return ResponseEntity.ok(MsaResponse.ok(stockBffService.updateWatchItem(authentication, request, response, itemId, watchItemRequest)));
    }

    @DeleteMapping("/stock/watch-items/{itemId}")
    public ResponseEntity<MsaResponse<Void>> deleteWatchItem(Authentication authentication, HttpServletRequest request, HttpServletResponse response, @PathVariable Long itemId) {
        stockBffService.deleteWatchItem(authentication, request, response, itemId);
        return ResponseEntity.ok(MsaResponse.ok(null));
    }
}
