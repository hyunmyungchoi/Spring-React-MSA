package com.springmsa.adminbff.user.dto;

import java.util.List;

public record AdminUserResponse(
        Long userId,
        String loginId,
        String email,
        String username,
        boolean enabled,
        List<String> roles
) {
}
