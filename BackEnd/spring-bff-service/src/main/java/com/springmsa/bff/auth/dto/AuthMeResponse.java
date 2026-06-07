package com.springmsa.bff.auth.dto;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;

@NullMarked
public record AuthMeResponse(
        boolean authenticated,
        @Nullable  Map<String, Object> user
) {
    public static AuthMeResponse anonymous() {
        return new AuthMeResponse(false, null);
    }

    public static AuthMeResponse authenticated(Map<String, Object> user) {
        return new AuthMeResponse(true, user);
    }
}