package com.springmsa.adminbff.registration;

import com.springmsa.adminbff.common.dto.AdminApiResponse;
import com.springmsa.adminbff.registration.dto.AdminRegistrationRequest;
import com.springmsa.adminbff.registration.dto.AdminRegistrationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
    public ResponseEntity<AdminApiResponse<AdminRegistrationResponse>> registerAdmin(@RequestBody AdminRegistrationRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(AdminApiResponse.created(adminBffRegistrationService.registerAdmin(request)));
    }

    /**
     * Converts registration failures into a stable JSON error response.
     *
     * @param e service-layer registration exception
     * @return error response with the exception status code
     */
    @ExceptionHandler(AdminBffRegistrationException.class)
    public ResponseEntity<AdminApiResponse<Void>> handleAdminBffRegistrationException(AdminBffRegistrationException e) {
        return ResponseEntity
                .status(e.statusCode())
                .body(AdminApiResponse.failure(e.statusCode(), e.getMessage()));
    }
}
