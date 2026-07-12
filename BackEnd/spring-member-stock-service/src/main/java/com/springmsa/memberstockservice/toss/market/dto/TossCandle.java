package com.springmsa.memberstockservice.toss.market.dto;

public record TossCandle(
        String timestamp,
        String openPrice,
        String highPrice,
        String lowPrice,
        String closePrice,
        String volume,
        String currency
) {
}
