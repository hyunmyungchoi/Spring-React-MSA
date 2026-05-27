package com.springmsa.authserver.otp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record WhatsAppOtpVerifyRequest(
        @NotBlank(message = "WhatsApp number is required")
        @Pattern(
                regexp = "^\\+?[0-9]{10,15}$",
                message = "Invalid WhatsApp number format"
        )
        String whatsappNumber,

        @NotBlank(message = "OTP is required")
        @Pattern(regexp = "\\d{6}", message = "OTP must be 6 digits")
        String otp
) {
}