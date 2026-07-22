package com.springmsa.adminbff.common.error;

import com.springmsa.common.web.error.AbstractMsaExceptionHandler;
import com.springmsa.common.web.response.MsaResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler extends AbstractMsaExceptionHandler {

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<MsaResponse<Void>> handleNoResourceFoundException(NoResourceFoundException exception) {
        return errorResponse(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "Resource not found",
                List.of()
        );
    }
}
