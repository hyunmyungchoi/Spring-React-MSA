package com.springmsa.memberstockservice.api.dto;

import java.time.Instant;

public record StockWatchItemResponse(
        Long id,
        String symbol,
        String memo,
        String owner,
        Instant createdAt,
        Instant updatedAt
) {
}
