package com.springmsa.memberbff.auth.dto;

public record LogoutResponse(
        String logout,
        boolean authServerLogoutRequired,
        String authServerLogoutUrl
) {

    public static LogoutResponse success(String authServerLogoutUrl) {
        return new LogoutResponse("success", true, authServerLogoutUrl);
    }
}
