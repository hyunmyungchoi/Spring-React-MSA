package com.springmsa.common.web.error;

import com.springmsa.common.web.response.FieldErrorResponse;

import java.util.List;

public record DownstreamErrorResponse(
        String code,
        String message,
        List<FieldErrorResponse> errors
) {

    public DownstreamErrorResponse {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public DownstreamErrorResponse withFallbacks(String fallbackCode, String fallbackMessage) {
        return new DownstreamErrorResponse(
                hasText(code) ? code : fallbackCode,
                hasText(message) ? message : fallbackMessage,
                errors
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
