package com.springmsa.memberbff.userbff.dto;

import java.util.List;

public record CurrentUserResponse(
        String sub,
        Long userId,
        String loginId,
        String email,
        List<String> roles
) {
}
