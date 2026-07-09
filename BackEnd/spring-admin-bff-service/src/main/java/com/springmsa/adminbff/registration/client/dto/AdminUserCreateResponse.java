package com.springmsa.adminbff.registration.client.dto;

import java.util.Set;

public record AdminUserCreateResponse(
        Long userId,
        String loginId,
        String email,
        String username,
        boolean enabled,
        Set<String> roles
) {
}
