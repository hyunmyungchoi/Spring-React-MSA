package com.springmsa.memberbff.stockbff.dto;

public record PartialFailure(
        String component,
        String code,
        String message,
        String traceId
) {
}
