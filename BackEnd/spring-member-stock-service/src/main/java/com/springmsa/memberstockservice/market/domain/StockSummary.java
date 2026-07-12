package com.springmsa.memberstockservice.market.domain;

public record StockSummary(
        String symbol,
        String name,
        String englishName,
        String market,
        String currency,
        String status
) {
}
