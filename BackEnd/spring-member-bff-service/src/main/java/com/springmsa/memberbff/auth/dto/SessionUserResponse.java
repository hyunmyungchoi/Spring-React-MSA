package com.springmsa.memberbff.auth.dto;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

@NullMarked
public record SessionUserResponse(
        @Nullable String sub,
        @Nullable String name,
        @Nullable String username,
        @Nullable Long userId,
        @Nullable String loginId,
        @Nullable String email,
        List<String> roles
) {
}
