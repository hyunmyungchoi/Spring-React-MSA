package com.springmsa.authserver.otp.common;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.otp")
public class OtpProperties {

    private boolean exposeDevOtp = false;
    private Email email = new Email();
    private WhatsApp whatsapp = new WhatsApp();

    public boolean isExposeDevOtp() {
        return exposeDevOtp;
    }

    public void setExposeDevOtp(boolean exposeDevOtp) {
        this.exposeDevOtp = exposeDevOtp;
    }

    public Email getEmail() {
        return email;
    }

    public void setEmail(Email email) {
        this.email = email;
    }

    public WhatsApp getWhatsapp() {
        return whatsapp;
    }

    public void setWhatsapp(WhatsApp whatsapp) {
        this.whatsapp = whatsapp;
    }

    public static class Email {
        private long ttlSeconds = 180;
        private String sender = "dev";
        private String from;
        private long resendCooldownSeconds = 60;
        private int maxVerifyAttempts = 5;

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        public String getSender() {
            return sender;
        }

        public void setSender(String sender) {
            this.sender = sender;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public long getResendCooldownSeconds() {
            return resendCooldownSeconds;
        }

        public void setResendCooldownSeconds(long resendCooldownSeconds) {
            this.resendCooldownSeconds = resendCooldownSeconds;
        }

        public int getMaxVerifyAttempts() {
            return maxVerifyAttempts;
        }

        public void setMaxVerifyAttempts(int maxVerifyAttempts) {
            this.maxVerifyAttempts = maxVerifyAttempts;
        }
    }

    public static class WhatsApp {
        private long ttlSeconds = 180;
        private long resendCooldownSeconds = 60;
        private int maxVerifyAttempts = 5;
        private String sender = "dev";
        private String graphApiBaseUrl = "https://graph.facebook.com/v25.0";
        private String phoneNumberId;
        private String accessToken;
        private String templateName = "authentication_code_copy_code_button";
        private String languageCode = "en_US";

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        public long getResendCooldownSeconds() {
            return resendCooldownSeconds;
        }

        public void setResendCooldownSeconds(long resendCooldownSeconds) {
            this.resendCooldownSeconds = resendCooldownSeconds;
        }

        public int getMaxVerifyAttempts() {
            return maxVerifyAttempts;
        }

        public void setMaxVerifyAttempts(int maxVerifyAttempts) {
            this.maxVerifyAttempts = maxVerifyAttempts;
        }
        public String getSender() {
            return sender;
        }

        public void setSender(String sender) {
            this.sender = sender;
        }

        public String getGraphApiBaseUrl() {
            return graphApiBaseUrl;
        }

        public void setGraphApiBaseUrl(String graphApiBaseUrl) {
            this.graphApiBaseUrl = graphApiBaseUrl;
        }

        public String getPhoneNumberId() {
            return phoneNumberId;
        }

        public void setPhoneNumberId(String phoneNumberId) {
            this.phoneNumberId = phoneNumberId;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getTemplateName() {
            return templateName;
        }

        public void setTemplateName(String templateName) {
            this.templateName = templateName;
        }

        public String getLanguageCode() {
            return languageCode;
        }

        public void setLanguageCode(String languageCode) {
            this.languageCode = languageCode;
        }
    }
}