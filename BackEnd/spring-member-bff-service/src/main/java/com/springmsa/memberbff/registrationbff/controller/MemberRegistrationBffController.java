package com.springmsa.memberbff.registrationbff.controller;

import com.springmsa.common.web.response.MsaResponse;
import com.springmsa.memberbff.registrationbff.dto.RegistrationRequest;
import com.springmsa.memberbff.registrationbff.dto.RegistrationResponse;
import com.springmsa.memberbff.registrationbff.service.MemberRegistrationBffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MemberRegistrationBffController {

    private final MemberRegistrationBffService memberRegistrationBffService;

    @PostMapping("/registration/member")
    public ResponseEntity<MsaResponse<RegistrationResponse>> registerMember(@Valid @RequestBody RegistrationRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(MsaResponse.created(memberRegistrationBffService.registerMember(request)));
    }
}
