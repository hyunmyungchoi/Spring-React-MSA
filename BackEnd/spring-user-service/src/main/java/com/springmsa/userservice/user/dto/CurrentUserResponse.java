package com.springmsa.userservice.user.dto;

import org.jspecify.annotations.NullMarked;

import java.util.List;

@NullMarked
public record CurrentUserResponse(
        String sub,
        Long userId,
        String loginId,
        String email,
        List<String> roles
) {
}
