package com.springmsa.memberstockservice.api.controller;

import com.springmsa.memberstockservice.api.dto.StockWatchItemRequest;
import com.springmsa.memberstockservice.api.dto.StockWatchItemResponse;
import com.springmsa.memberstockservice.api.service.StockWatchItemService;
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
public class StockWatchItemController {

    private final StockWatchItemService stockWatchItemService;

    public StockWatchItemController(StockWatchItemService stockWatchItemService) {
        this.stockWatchItemService = stockWatchItemService;
    }

    @GetMapping
    public List<StockWatchItemResponse> findAll() {
        return stockWatchItemService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StockWatchItemResponse create(@RequestBody StockWatchItemRequest request, Authentication authentication) {
        return stockWatchItemService.create(request, authentication.getName());
    }

    @PutMapping("/{itemId}")
    public StockWatchItemResponse update(@PathVariable Long itemId, @RequestBody StockWatchItemRequest request) {
        return stockWatchItemService.update(itemId, request);
    }

    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long itemId) {
        stockWatchItemService.delete(itemId);
    }
}
