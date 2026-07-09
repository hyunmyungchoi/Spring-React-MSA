package com.springmsa.authserver.common.error;

import com.springmsa.common.web.error.MsaErrorCode;
import org.springframework.http.HttpStatus;

public enum ErrorCode implements MsaErrorCode {
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "Invalid login ID or password"),
    USER_SERVICE_ERROR(HttpStatus.BAD_GATEWAY, "USER_SERVICE_ERROR", "User service error");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
