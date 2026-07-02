package com.springmsa.adminbff.auth.dto;

public record AdminLogoutResponse(
        String logout,
        boolean authServerLogoutRequired,
        String authServerLogoutUrl
) {

    public static AdminLogoutResponse success(String authServerLogoutUrl) {
        return new AdminLogoutResponse("success", true, authServerLogoutUrl);
    }
}
