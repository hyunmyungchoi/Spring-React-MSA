package com.springmsa.memberstockservice.api;

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
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/stock/watch-items")
public class StockWatchItemController {

    private final AtomicLong sequence = new AtomicLong(0);
    private final ConcurrentHashMap<Long, StockWatchItemResponse> watchItems = new ConcurrentHashMap<>();

    @GetMapping
    public List<StockWatchItemResponse> findAll() {
        return watchItems.values().stream()
                .sorted(Comparator.comparing(StockWatchItemResponse::id))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StockWatchItemResponse create(@RequestBody StockWatchItemRequest request, Authentication authentication) {
        Long id = sequence.incrementAndGet();
        Instant now = Instant.now();
        StockWatchItemResponse response = new StockWatchItemResponse(
                id,
                request.symbol(),
                request.memo(),
                authentication.getName(),
                now,
                now
        );
        watchItems.put(id, response);
        return response;
    }

    @PutMapping("/{itemId}")
    public StockWatchItemResponse update(@PathVariable Long itemId, @RequestBody StockWatchItemRequest request) {
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

    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long itemId) {
        if (watchItems.remove(itemId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock watch item not found");
        }
    }
}
