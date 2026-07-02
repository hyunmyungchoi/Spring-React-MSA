package com.springmsa.memberbff.registration;

import com.springmsa.memberbff.registration.dto.RegistrationRequest;
import com.springmsa.memberbff.registration.dto.RegistrationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    @PostMapping("/registration/member")
    public ResponseEntity<RegistrationResponse> registerMember(@RequestBody RegistrationRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(registrationService.registerMember(request));
    }

    @ExceptionHandler(RegistrationException.class)
    public ResponseEntity<RegistrationErrorResponse> handleRegistrationException(RegistrationException e) {
        return ResponseEntity
                .status(e.statusCode())
                .body(RegistrationErrorResponse.failed(e.statusCode().value(), e.getMessage()));
    }
}
