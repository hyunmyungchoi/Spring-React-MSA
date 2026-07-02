package com.springmsa.adminbff.user.dto;

import java.util.List;

public record AdminCurrentUserResponse(
        String sub,
        Long userId,
        String loginId,
        String email,
        List<String> roles
) {
}
