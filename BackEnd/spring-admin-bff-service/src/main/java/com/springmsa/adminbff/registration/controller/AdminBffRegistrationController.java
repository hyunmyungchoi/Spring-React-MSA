package com.springmsa.adminbff.registration.controller;

import com.springmsa.adminbff.registration.dto.AdminRegistrationRequest;
import com.springmsa.adminbff.registration.dto.AdminRegistrationResponse;
import com.springmsa.adminbff.registration.service.AdminBffRegistrationService;
import com.springmsa.common.web.response.MsaResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminBffRegistrationController {

    private final AdminBffRegistrationService adminBffRegistrationService;

    /**
     * Registers a new admin account through the user-service internal API.
     *
     * @param request admin registration payload
     * @return created admin registration response
     */
    @PostMapping("/registration/admin")
    public ResponseEntity<MsaResponse<AdminRegistrationResponse>> registerAdmin(@Valid @RequestBody AdminRegistrationRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(MsaResponse.created(adminBffRegistrationService.registerAdmin(request)));
    }
}
