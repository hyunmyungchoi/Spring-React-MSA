package com.springmsa.memberstockservice.market.dto;

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
