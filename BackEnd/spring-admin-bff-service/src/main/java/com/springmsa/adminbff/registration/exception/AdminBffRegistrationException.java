package com.springmsa.adminbff.registration.exception;

import com.springmsa.common.web.error.ApiException;
import org.springframework.http.HttpStatusCode;

public class AdminBffRegistrationException extends ApiException {

    private static final String CODE = "ADMIN_REGISTRATION_FAILED";

    public AdminBffRegistrationException(HttpStatusCode statusCode, String message) {
        super(statusCode, CODE, message);
    }

    public AdminBffRegistrationException(HttpStatusCode statusCode, String message, Throwable cause) {
        super(statusCode, CODE, message, cause);
    }

    public HttpStatusCode statusCode() {
        return status();
    }
}
