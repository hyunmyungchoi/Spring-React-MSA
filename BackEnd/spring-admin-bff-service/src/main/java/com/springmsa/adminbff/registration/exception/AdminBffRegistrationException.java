package com.springmsa.adminbff.registration.exception;

import com.springmsa.common.web.error.ApiException;
import com.springmsa.common.web.response.FieldErrorResponse;
import org.springframework.http.HttpStatusCode;

import java.util.List;

public class AdminBffRegistrationException extends ApiException {

    public static final String CODE = "ADMIN_REGISTRATION_FAILED";

    public AdminBffRegistrationException(HttpStatusCode statusCode, String message) {
        super(statusCode, CODE, message);
    }

    public AdminBffRegistrationException(HttpStatusCode statusCode, String message, Throwable cause) {
        super(statusCode, CODE, message, cause);
    }

    public AdminBffRegistrationException(
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
