package com.springmsa.memberbff.registration;

public record RegistrationErrorResponse(
        boolean success,
        int status,
        String message
) {

    public static RegistrationErrorResponse failed(int status, String message) {
        return new RegistrationErrorResponse(false, status, message);
    }
}
