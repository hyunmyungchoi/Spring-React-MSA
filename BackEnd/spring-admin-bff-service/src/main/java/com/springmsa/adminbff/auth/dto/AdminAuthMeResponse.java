package com.springmsa.adminbff.auth.dto;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;

@NullMarked
public record AdminAuthMeResponse(
        boolean authenticated,
        @Nullable Map<String, Object> user,
        @Nullable String reason
) {

    public static AdminAuthMeResponse anonymous() {
        return new AdminAuthMeResponse(false, null, null);
    }

    public static AdminAuthMeResponse adminRoleRequired() {
        return new AdminAuthMeResponse(false, null, "admin_role_required");
    }

    public static AdminAuthMeResponse authenticated(Map<String, Object> user) {
        return new AdminAuthMeResponse(true, user, null);
    }
}