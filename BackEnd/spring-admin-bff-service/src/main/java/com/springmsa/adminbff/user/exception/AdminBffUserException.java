package com.springmsa.adminbff.user.exception;

import com.springmsa.common.web.error.ApiException;
import com.springmsa.common.web.response.FieldErrorResponse;
import org.springframework.http.HttpStatusCode;

import java.util.List;

public class AdminBffUserException extends ApiException {

    public static final String CODE = "ADMIN_USER_REQUEST_FAILED";

    public AdminBffUserException(HttpStatusCode statusCode, String message) {
        super(statusCode, CODE, message);
    }

    public AdminBffUserException(HttpStatusCode statusCode, String message, Throwable cause) {
        super(statusCode, CODE, message, cause);
    }

    public AdminBffUserException(
            HttpStatusCode statusCode,
            String code,
            String message,
            List<FieldErrorResponse> errors,
            Throwable cause
    ) {
        super(statusCode, code, message, errors, cause);
    }

    public HttpStatusCode statusCode() {
        return status();
    }
}
