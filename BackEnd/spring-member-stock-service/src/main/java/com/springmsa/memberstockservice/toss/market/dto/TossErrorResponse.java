package com.springmsa.memberstockservice.toss.market.dto;

public record TossErrorResponse(
        String code,
        String message
) {
}
