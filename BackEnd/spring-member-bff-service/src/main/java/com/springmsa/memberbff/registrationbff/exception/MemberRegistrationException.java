package com.springmsa.memberbff.registrationbff.exception;

import com.springmsa.common.web.error.ApiException;
import com.springmsa.common.web.response.FieldErrorResponse;
import org.springframework.http.HttpStatusCode;

import java.util.List;

public class MemberRegistrationException extends ApiException {

    public static final String CODE = "REGISTRATION_FAILED";

    public MemberRegistrationException(HttpStatusCode statusCode, String message) {
        super(statusCode, CODE, message);
    }

    public MemberRegistrationException(HttpStatusCode statusCode, String message, Throwable cause) {
        super(statusCode, CODE, message, cause);
    }

    public MemberRegistrationException(
            HttpStatusCode statusCode,
            String code,
            String message,
            List<FieldErrorResponse> errors,
            Throwable cause
    ) {
        super(statusCode, code, message, errors, cause);
    }

    public HttpStatusCode statusCode() {
        return status();
    }
}
