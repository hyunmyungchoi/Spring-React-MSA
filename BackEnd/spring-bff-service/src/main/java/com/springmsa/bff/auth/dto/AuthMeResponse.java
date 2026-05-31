package com.springmsa.bff.auth.dto;

import java.util.Map;

public record AuthMeResponse(
        boolean authenticated,
        Map<String, Object> user
) {
    public static AuthMeResponse anonymous() {
        return new AuthMeResponse(false, null);
    }

    public static AuthMeResponse authenticated(Map<String, Object> user) {
        return new AuthMeResponse(true, user);
    }
}