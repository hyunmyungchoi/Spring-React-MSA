package com.springmsa.memberstockservice.stock.service;

import com.springmsa.memberstockservice.stock.dto.StockWatchItemRequest;
import com.springmsa.memberstockservice.stock.dto.StockWatchItemResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class StockWatchItemService {

    private final AtomicLong sequence = new AtomicLong(0);
    private final ConcurrentHashMap<Long, StockWatchItemResponse> watchItems = new ConcurrentHashMap<>();

    public List<StockWatchItemResponse> findAll() {
        return watchItems.values().stream()
                .sorted(Comparator.comparing(StockWatchItemResponse::id))
                .toList();
    }

    public StockWatchItemResponse create(StockWatchItemRequest request, String owner) {
        Long id = sequence.incrementAndGet();
        Instant now = Instant.now();
        StockWatchItemResponse response = new StockWatchItemResponse(
                id,
                request.symbol(),
                request.memo(),
                owner,
                now,
                now
        );
        watchItems.put(id, response);
        return response;
    }

    public StockWatchItemResponse update(Long itemId, StockWatchItemRequest request) {
        StockWatchItemResponse current = watchItems.get(itemId);

        if (current == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock watch item not found");
        }

        StockWatchItemResponse updated = new StockWatchItemResponse(
                current.id(),
                request.symbol(),
                request.memo(),
                current.owner(),
                current.createdAt(),
                Instant.now()
        );
        watchItems.put(itemId, updated);
        return updated;
    }

    public void delete(Long itemId) {
        if (watchItems.remove(itemId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock watch item not found");
        }
    }
}
