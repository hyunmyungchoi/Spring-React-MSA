package com.springmsa.memberbff.stockbff.dto;

import java.time.Instant;

public record StockSummaryResponse(
        String symbol,
        String name,
        String englishName,
        String market,
        String currency,
        String status,
        Instant fetchedAt,
        DataStatus dataStatus
) {
}
