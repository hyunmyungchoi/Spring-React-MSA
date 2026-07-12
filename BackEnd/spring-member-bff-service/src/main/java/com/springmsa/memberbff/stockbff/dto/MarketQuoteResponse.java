package com.springmsa.memberbff.stockbff.dto;

import java.time.Instant;

public record MarketQuoteResponse(
        String symbol,
        String lastPrice,
        String currency,
        String timestamp,
        Instant fetchedAt,
        DataStatus dataStatus
) {
}
