package com.springmsa.common.web.response;

public record FieldErrorResponse(
        String field,
        String message
) {
}
