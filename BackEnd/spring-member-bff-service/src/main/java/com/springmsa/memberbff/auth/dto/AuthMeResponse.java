package com.springmsa.memberbff.auth.dto;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record AuthMeResponse(
        boolean authenticated,
        @Nullable SessionUserResponse user
) {
    public static AuthMeResponse anonymous() {
        return new AuthMeResponse(false, null);
    }

    public static AuthMeResponse authenticated(SessionUserResponse user) {
        return new AuthMeResponse(true, user);
    }
}
