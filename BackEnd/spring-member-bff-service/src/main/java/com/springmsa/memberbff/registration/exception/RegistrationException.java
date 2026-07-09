package com.springmsa.memberbff.registration.exception;

import com.springmsa.common.web.error.ApiException;
import org.springframework.http.HttpStatusCode;

public class RegistrationException extends ApiException {

    private static final String CODE = "REGISTRATION_FAILED";

    public RegistrationException(HttpStatusCode statusCode, String message) {
        super(statusCode, CODE, message);
    }

    public RegistrationException(HttpStatusCode statusCode, String message, Throwable cause) {
        super(statusCode, CODE, message, cause);
    }

    public HttpStatusCode statusCode() {
        return status();
    }
}
