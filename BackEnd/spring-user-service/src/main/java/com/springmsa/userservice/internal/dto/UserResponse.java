package com.springmsa.userservice.internal.dto;

import com.springmsa.userservice.domain.User;

import java.util.Set;

public record UserResponse(
        Long userId,
        String loginId,
        String email,
        String username,
        boolean enabled,
        Set<String> roles
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getUserId(),
                user.getLoginId(),
                user.getEmail(),
                user.getUsername(),
                user.isEnabled(),
                user.getRoles()
        );
    }
}
