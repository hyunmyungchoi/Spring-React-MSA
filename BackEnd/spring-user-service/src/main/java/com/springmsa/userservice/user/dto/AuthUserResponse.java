package com.springmsa.userservice.user.dto;

import java.util.Set;

public record AuthUserResponse(
        Long userId,
        String loginId,
        String email,
        String username,
        String phoneNumber,
        String whatsappNumber,
        String password,
        boolean enabled,
        Set<String> roles
) {
}
