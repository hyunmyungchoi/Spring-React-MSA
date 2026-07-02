package com.springmsa.adminbff.registration;

import org.springframework.http.HttpStatusCode;

public class AdminBffRegistrationException extends RuntimeException {

    private final HttpStatusCode statusCode;

    public AdminBffRegistrationException(HttpStatusCode statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public AdminBffRegistrationException(HttpStatusCode statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public HttpStatusCode statusCode() {
        return statusCode;
    }
}
