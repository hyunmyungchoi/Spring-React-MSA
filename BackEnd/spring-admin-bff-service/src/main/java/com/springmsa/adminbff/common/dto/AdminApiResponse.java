package com.springmsa.adminbff.common.dto;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public record AdminApiResponse<T>(
        boolean success,
        int status,
        T data,
        String message
) {

    public static <T> AdminApiResponse<T> ok(T data) {
        return success(HttpStatus.OK, data);
    }

    public static <T> AdminApiResponse<T> created(T data) {
        return success(HttpStatus.CREATED, data);
    }

    public static <T> AdminApiResponse<T> success(HttpStatusCode status, T data) {
        return new AdminApiResponse<>(true, status.value(), data, null);
    }

    public static AdminApiResponse<Void> failure(HttpStatusCode status, String message) {
        return new AdminApiResponse<>(false, status.value(), null, message);
    }
}
