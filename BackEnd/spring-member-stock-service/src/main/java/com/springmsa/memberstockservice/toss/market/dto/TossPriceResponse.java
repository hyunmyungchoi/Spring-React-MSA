package com.springmsa.memberstockservice.toss.market.dto;

public record TossPriceResponse(
        String symbol,
        String timestamp,
        String lastPrice,
        String currency
) {
}
