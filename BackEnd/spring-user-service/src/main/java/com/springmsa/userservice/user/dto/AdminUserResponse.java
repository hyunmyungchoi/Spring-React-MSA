package com.springmsa.userservice.user.dto;

import com.springmsa.userservice.user.domain.User;
import org.jspecify.annotations.NullMarked;

import java.util.List;

@NullMarked
public record AdminUserResponse(
        Long userId,
        String loginId,
        String email,
        String username,
        boolean enabled,
        List<String> roles
) {

    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getUserId(),
                user.getLoginId(),
                user.getEmail(),
                user.getUsername(),
                user.isEnabled(),
                user.getRoles().stream()
                        .sorted()
                        .toList()
        );
    }
}
