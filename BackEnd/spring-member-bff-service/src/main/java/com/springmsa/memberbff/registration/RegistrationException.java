package com.springmsa.memberbff.registration;

import org.springframework.http.HttpStatusCode;

public class RegistrationException extends RuntimeException {

    private final HttpStatusCode statusCode;

    public RegistrationException(HttpStatusCode statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public RegistrationException(HttpStatusCode statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public HttpStatusCode statusCode() {
        return statusCode;
    }
}
