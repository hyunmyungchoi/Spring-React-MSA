package com.springmsa.common.web.error;

import org.springframework.http.HttpStatusCode;

public class ApiException extends RuntimeException {

    private final HttpStatusCode status;
    private final String code;

    public ApiException(MsaErrorCode errorCode) {
        this(errorCode, null);
    }

    public ApiException(MsaErrorCode errorCode, Throwable cause) {
        this(errorCode.status(), errorCode.code(), errorCode.message(), cause);
    }

    public ApiException(HttpStatusCode status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(HttpStatusCode status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatusCode status() {
        return status;
    }

    public String code() {
        return code;
    }
}
