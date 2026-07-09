package com.springmsa.common.web.error;

import com.springmsa.common.web.response.FieldErrorResponse;
import org.springframework.http.HttpStatusCode;

import java.util.List;

public class ApiException extends RuntimeException {

    private final HttpStatusCode status;
    private final String code;
    private final List<FieldErrorResponse> errors;

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
        this(status, code, message, List.of(), cause);
    }

    public ApiException(
            HttpStatusCode status,
            String code,
            String message,
            List<FieldErrorResponse> errors,
            Throwable cause
    ) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public HttpStatusCode status() {
        return status;
    }

    public String code() {
        return code;
    }

    public List<FieldErrorResponse> errors() {
        return errors;
    }
}
