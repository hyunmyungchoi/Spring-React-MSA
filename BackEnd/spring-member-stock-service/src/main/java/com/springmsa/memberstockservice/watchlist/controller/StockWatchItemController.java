package com.springmsa.memberstockservice.watchlist.controller;

import com.springmsa.memberstockservice.watchlist.dto.StockWatchItemRequest;
import com.springmsa.memberstockservice.watchlist.dto.StockWatchItemResponse;
import com.springmsa.memberstockservice.watchlist.service.StockWatchItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stock/watch-items")
@RequiredArgsConstructor
public class StockWatchItemController {

    private final StockWatchItemService stockWatchItemService;

    @GetMapping
    public List<StockWatchItemResponse> findAll(Authentication authentication) {
        return stockWatchItemService.findAll(authentication.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StockWatchItemResponse create(@RequestBody StockWatchItemRequest request, Authentication authentication) {
        return stockWatchItemService.create(request, authentication.getName());
    }

    @PutMapping("/{itemId}")
    public StockWatchItemResponse update(
            @PathVariable Long itemId,
            @RequestBody StockWatchItemRequest request,
            Authentication authentication
    ) {
        return stockWatchItemService.update(itemId, request, authentication.getName());
    }

    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long itemId, Authentication authentication) {
        stockWatchItemService.delete(itemId, authentication.getName());
    }
}
