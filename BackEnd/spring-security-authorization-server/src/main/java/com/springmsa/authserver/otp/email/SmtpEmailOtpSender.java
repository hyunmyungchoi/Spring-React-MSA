package com.springmsa.authserver.otp.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.otp.email.sender",
        havingValue = "smtp"
)
@ConditionalOnBean(JavaMailSender.class)
public class SmtpEmailOtpSender implements EmailOtpSender {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailOtpSender(
            JavaMailSender mailSender,
            @Value("${app.otp.email.from}") String from
    ) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendOtp(String email, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("[Spring MSA] Email OTP Verification Code");
        message.setText("""
                Your verification code is:

                %s

                This code will expire in 3 minutes.
                """.formatted(otp));

        mailSender.send(message);
    }
}