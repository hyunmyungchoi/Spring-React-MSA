package com.springmsa.memberstockservice.market.dto;

import java.time.Instant;

public record CandleResponse(
        String timestamp,
        String openPrice,
        String highPrice,
        String lowPrice,
        String closePrice,
        String volume,
        String currency,
        Instant fetchedAt,
        DataStatus dataStatus
) {
}
