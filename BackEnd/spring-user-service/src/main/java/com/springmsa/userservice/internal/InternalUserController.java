package com.springmsa.userservice.internal;

import com.springmsa.userservice.internal.dto.UserResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final InternalUserQueryService internalUserQueryService;

    public InternalUserController(InternalUserQueryService internalUserQueryService) {
        this.internalUserQueryService = internalUserQueryService;
    }

    @GetMapping("/login-id/{loginId}")
    public UserResponse findByLoginId(@PathVariable String loginId) {
        return internalUserQueryService.findByLoginId(loginId);
    }
}
