package com.springmsa.common.web.response;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.util.List;

public record MsaResponse<T>(
        boolean success,
        String code,
        String message,
        int status,
        T data,
        List<FieldErrorResponse> errors
) {

    public static <T> MsaResponse<T> ok(T data) {
        return success(HttpStatus.OK, "OK", "OK", data);
    }

    public static <T> MsaResponse<T> created(T data) {
        return success(HttpStatus.CREATED, "CREATED", "Created", data);
    }

    public static <T> MsaResponse<T> success(HttpStatusCode status, String code, String message, T data) {
        return new MsaResponse<>(true, code, message, status.value(), data, List.of());
    }

    public static MsaResponse<Void> fail(HttpStatusCode status, String code, String message) {
        return fail(status, code, message, List.of());
    }

    public static MsaResponse<Void> fail(
            HttpStatusCode status,
            String code,
            String message,
            List<FieldErrorResponse> errors
    ) {
        return fail(status, code, message, null, errors);
    }

    public static <T> MsaResponse<T> fail(
            HttpStatusCode status,
            String code,
            String message,
            T data,
            List<FieldErrorResponse> errors
    ) {
        return new MsaResponse<>(false, code, message, status.value(), data, errors);
    }
}
