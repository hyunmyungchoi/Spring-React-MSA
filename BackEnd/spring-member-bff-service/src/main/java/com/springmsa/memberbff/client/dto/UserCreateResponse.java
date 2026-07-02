package com.springmsa.memberbff.client.dto;

import java.util.Set;

public record UserCreateResponse(
        Long userId,
        String loginId,
        String email,
        String username,
        boolean enabled,
        Set<String> roles
) {
}
