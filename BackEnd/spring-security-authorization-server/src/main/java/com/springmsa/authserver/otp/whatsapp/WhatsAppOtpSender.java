package com.springmsa.authserver.otp.whatsapp;

public interface WhatsAppOtpSender {

    void sendOtp(String whatsappNumber, String otp);
}