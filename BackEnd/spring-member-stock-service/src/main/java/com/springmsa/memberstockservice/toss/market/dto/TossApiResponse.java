package com.springmsa.memberstockservice.toss.market.dto;

public record TossApiResponse<T>(
        T result
) {
}
