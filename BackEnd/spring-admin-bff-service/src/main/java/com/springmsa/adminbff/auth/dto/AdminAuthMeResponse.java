package com.springmsa.adminbff.auth.dto;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record AdminAuthMeResponse(
        boolean authenticated,
        @Nullable AdminSessionUserResponse user,
        @Nullable String reason
) {

    public static AdminAuthMeResponse anonymous() {
        return new AdminAuthMeResponse(false, null, null);
    }

    public static AdminAuthMeResponse adminRoleRequired() {
        return new AdminAuthMeResponse(false, null, "admin_role_required");
    }

    public static AdminAuthMeResponse authenticated(AdminSessionUserResponse user) {
        return new AdminAuthMeResponse(true, user, null);
    }
}
