package com.springmsa.memberbff.registration.controller;

import com.springmsa.common.web.response.MsaResponse;
import com.springmsa.memberbff.registration.dto.RegistrationRequest;
import com.springmsa.memberbff.registration.dto.RegistrationResponse;
import com.springmsa.memberbff.registration.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    @PostMapping("/registration/member")
    public ResponseEntity<MsaResponse<RegistrationResponse>> registerMember(@RequestBody RegistrationRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(MsaResponse.created(registrationService.registerMember(request)));
    }
}
