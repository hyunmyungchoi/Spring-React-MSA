package com.springmsa.userservice.api.dto;

import java.util.Set;

public record AuthUserResponse(
        Long userId,
        String loginId,
        String email,
        String username,
        String password,
        boolean enabled,
        Set<String> roles
) {
}