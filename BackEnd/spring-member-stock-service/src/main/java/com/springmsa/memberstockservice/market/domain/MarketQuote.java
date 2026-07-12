package com.springmsa.memberstockservice.market.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MarketQuote(
        String symbol,
        BigDecimal lastPrice,
        String currency,
        OffsetDateTime timestamp
) {
}
