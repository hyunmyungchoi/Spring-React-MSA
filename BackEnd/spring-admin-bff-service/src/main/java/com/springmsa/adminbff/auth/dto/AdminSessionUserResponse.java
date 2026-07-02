package com.springmsa.adminbff.auth.dto;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

@NullMarked
public record AdminSessionUserResponse(
        @Nullable String sub,
        @Nullable String name,
        @Nullable Long userId,
        @Nullable String loginId,
        @Nullable String email,
        List<String> roles
) {
}
