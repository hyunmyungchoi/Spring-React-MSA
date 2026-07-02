package com.springmsa.adminbff.user;

import org.springframework.http.HttpStatusCode;

public class AdminBffUserException extends RuntimeException {

    private final HttpStatusCode statusCode;

    public AdminBffUserException(HttpStatusCode statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public AdminBffUserException(HttpStatusCode statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public HttpStatusCode statusCode() {
        return statusCode;
    }
}
