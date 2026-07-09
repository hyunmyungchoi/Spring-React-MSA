package com.springmsa.common.web.error;

import com.springmsa.common.web.response.FieldErrorResponse;
import com.springmsa.common.web.response.MsaResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

public abstract class AbstractMsaExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<MsaResponse<Void>> handleApiException(ApiException exception) {
        return errorResponse(exception.status(), exception.code(), exception.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<MsaResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
        List<FieldErrorResponse> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldErrorResponse)
                .toList();

        return errorResponse(CommonErrorCode.VALIDATION_FAILED, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<MsaResponse<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException exception) {
        return errorResponse(CommonErrorCode.INVALID_REQUEST_BODY, List.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<MsaResponse<Void>> handleResponseStatusException(ResponseStatusException exception) {
        return errorResponse(
                exception.getStatusCode(),
                responseStatusCode(exception),
                responseStatusMessage(exception),
                List.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MsaResponse<Void>> handleException(Exception exception) {
        return errorResponse(CommonErrorCode.INTERNAL_SERVER_ERROR, List.of());
    }

    protected ResponseEntity<MsaResponse<Void>> errorResponse(
            MsaErrorCode errorCode,
            List<FieldErrorResponse> errors
    ) {
        return errorResponse(errorCode.status(), errorCode.code(), errorCode.message(), errors);
    }

    protected ResponseEntity<MsaResponse<Void>> errorResponse(
            HttpStatusCode status,
            String code,
            String message,
            List<FieldErrorResponse> errors
    ) {
        return ResponseEntity
                .status(status)
                .body(MsaResponse.fail(status, code, message, errors));
    }

    private FieldErrorResponse toFieldErrorResponse(FieldError fieldError) {
        return new FieldErrorResponse(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private String responseStatusCode(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());

        if (status == null) {
            return "HTTP_" + exception.getStatusCode().value();
        }

        return status.name();
    }

    private String responseStatusMessage(ResponseStatusException exception) {
        if (exception.getReason() == null || exception.getReason().isBlank()) {
            return "Request failed";
        }

        return exception.getReason();
    }
}
