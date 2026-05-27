package com.springmsa.authserver.otp.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.otp.email.sender",
        havingValue = "dev",
        matchIfMissing = true
)
public class DevEmailOtpSender implements EmailOtpSender {

    private static final Logger log = LoggerFactory.getLogger(DevEmailOtpSender.class);

    @Override
    public void sendOtp(String email, String otp) {
        log.info("[DEV EMAIL OTP] email={}, otp={}", email, otp);
    }
}