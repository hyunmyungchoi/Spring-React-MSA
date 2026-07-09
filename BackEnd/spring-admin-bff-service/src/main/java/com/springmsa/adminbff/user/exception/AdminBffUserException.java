package com.springmsa.adminbff.user.exception;

import com.springmsa.common.web.error.ApiException;
import org.springframework.http.HttpStatusCode;

public class AdminBffUserException extends ApiException {

    private static final String CODE = "ADMIN_USER_REQUEST_FAILED";

    public AdminBffUserException(HttpStatusCode statusCode, String message) {
        super(statusCode, CODE, message);
    }

    public AdminBffUserException(HttpStatusCode statusCode, String message, Throwable cause) {
        super(statusCode, CODE, message, cause);
    }

    public HttpStatusCode statusCode() {
        return status();
    }
}
