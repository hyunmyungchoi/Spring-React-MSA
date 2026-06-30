package com.springmsa.userservice.internal.dto;

import java.util.Set;

public record CreateUserResponse(
        Long userId,
        String loginId,
        String email,
        String username,
        boolean enabled,
        Set<String> roles
) {
}
