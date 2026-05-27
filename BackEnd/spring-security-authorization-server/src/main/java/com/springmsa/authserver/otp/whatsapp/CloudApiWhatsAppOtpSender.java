package com.springmsa.authserver.otp.whatsapp;

import com.springmsa.authserver.otp.common.OtpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(
        name = "app.otp.whatsapp.sender",
        havingValue = "cloud-api"
)
public class CloudApiWhatsAppOtpSender implements WhatsAppOtpSender {

    private static final Logger log = LoggerFactory.getLogger(CloudApiWhatsAppOtpSender.class);

    private final OtpProperties otpProperties;
    private final RestClient restClient;

    public CloudApiWhatsAppOtpSender(OtpProperties otpProperties) {
        this.otpProperties = otpProperties;

        String baseUrl = otpProperties.getWhatsapp().getGraphApiBaseUrl();

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + otpProperties.getWhatsapp().getAccessToken())
                .build();
    }

    @Override
    public void sendOtp(String whatsappNumber, String otp) {
        validateRequiredProperties();

        String normalizedWhatsappNumber = normalizeWhatsappNumber(whatsappNumber);

        Map<String, Object> requestBody = buildAuthenticationTemplateRequest(
                normalizedWhatsappNumber,
                otp
        );

        try {
            Map<?, ?> response = restClient.post()
                    .uri("/{phoneNumberId}/messages", otpProperties.getWhatsapp().getPhoneNumberId())
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            log.info("[WhatsApp Cloud API OTP] message sent. whatsappNumber={}, response={}",
                    normalizedWhatsappNumber,
                    response
            );
        } catch (Exception e) {
            log.error("[WhatsApp Cloud API OTP] failed to send message. whatsappNumber={}",
                    normalizedWhatsappNumber,
                    e
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Failed to send WhatsApp OTP message"
            );
        }
    }

    private Map<String, Object> buildAuthenticationTemplateRequest(String whatsappNumber, String otp) {
        return Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", whatsappNumber,
                "type", "template",
                "template", Map.of(
                        "name", otpProperties.getWhatsapp().getTemplateName(),
                        "language", Map.of(
                                "code", otpProperties.getWhatsapp().getLanguageCode()
                        ),
                        "components", List.of(
                                Map.of(
                                        "type", "body",
                                        "parameters", List.of(
                                                Map.of(
                                                        "type", "text",
                                                        "text", otp
                                                )
                                        )
                                ),
                                Map.of(
                                        "type", "button",
                                        "sub_type", "url",
                                        "index", "0",
                                        "parameters", List.of(
                                                Map.of(
                                                        "type", "text",
                                                        "text", otp
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private String normalizeWhatsappNumber(String whatsappNumber) {
        return whatsappNumber.trim().replace("+", "");
    }

    private void validateRequiredProperties() {
        OtpProperties.WhatsApp whatsapp = otpProperties.getWhatsapp();

        if (!StringUtils.hasText(whatsapp.getGraphApiBaseUrl())) {
            throw new IllegalStateException("WhatsApp graphApiBaseUrl is required");
        }

        if (!StringUtils.hasText(whatsapp.getPhoneNumberId())) {
            throw new IllegalStateException("WhatsApp phoneNumberId is required");
        }

        if (!StringUtils.hasText(whatsapp.getAccessToken())) {
            throw new IllegalStateException("WhatsApp accessToken is required");
        }

        if (!StringUtils.hasText(whatsapp.getTemplateName())) {
            throw new IllegalStateException("WhatsApp templateName is required");
        }

        if (!StringUtils.hasText(whatsapp.getLanguageCode())) {
            throw new IllegalStateException("WhatsApp languageCode is required");
        }
    }
}