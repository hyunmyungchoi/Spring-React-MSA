package com.springmsa.userservice.user.controller;

import com.springmsa.common.web.response.MsaResponse;
import com.springmsa.userservice.user.dto.CreateUserRequest;
import com.springmsa.userservice.user.dto.CreateUserResponse;
import com.springmsa.userservice.user.service.InternalUserCreateService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
public class InternalUserCreateController {

    private final InternalUserCreateService internalUserCreateService;

    public InternalUserCreateController(InternalUserCreateService internalUserCreateService) {
        this.internalUserCreateService = internalUserCreateService;
    }

    @PostMapping
    public ResponseEntity<MsaResponse<CreateUserResponse>> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(MsaResponse.created(internalUserCreateService.create(request)));
    }
}
