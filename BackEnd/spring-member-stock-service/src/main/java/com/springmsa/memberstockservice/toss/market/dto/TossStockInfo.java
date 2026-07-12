package com.springmsa.memberstockservice.toss.market.dto;

public record TossStockInfo(
        String symbol,
        String name,
        String englishName,
        String market,
        String currency,
        String status
) {
}
