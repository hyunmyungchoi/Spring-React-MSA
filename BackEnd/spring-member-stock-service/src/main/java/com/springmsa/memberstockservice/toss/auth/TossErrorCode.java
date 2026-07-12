package com.springmsa.memberstockservice.toss.auth;

import com.springmsa.common.web.error.MsaErrorCode;
import org.springframework.http.HttpStatus;

enum TossErrorCode implements MsaErrorCode {
    TOSS_TOKEN_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "TOSS_TOKEN_UNAVAILABLE", "Toss OAuth token is unavailable");

    private final HttpStatus status;
    private final String code;
    private final String message;

    TossErrorCode(HttpStatus status, String code, String message) {
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
