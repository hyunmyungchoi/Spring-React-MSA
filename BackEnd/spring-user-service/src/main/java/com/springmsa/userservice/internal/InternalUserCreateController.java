package com.springmsa.userservice.internal;

import com.springmsa.userservice.internal.dto.CreateUserRequest;
import com.springmsa.userservice.internal.dto.CreateUserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
public class InternalUserCreateController {

    private final InternalUserCreateService internalUserCreateService;

    public InternalUserCreateController(InternalUserCreateService internalUserCreateService) {
        this.internalUserCreateService = internalUserCreateService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateUserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return internalUserCreateService.create(request);
    }
}
