package com.springmsa.authserver.otp.email;

public interface EmailOtpSender {

    void sendOtp(String email, String otp);
}