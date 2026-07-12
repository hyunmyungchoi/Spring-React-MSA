package com.springmsa.memberstockservice.market.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record Candle(
        OffsetDateTime timestamp,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal closePrice,
        BigDecimal volume,
        String currency
) {
}
