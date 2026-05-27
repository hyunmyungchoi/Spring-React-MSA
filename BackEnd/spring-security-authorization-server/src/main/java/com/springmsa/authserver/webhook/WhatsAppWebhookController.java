package com.springmsa.authserver.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/webhooks/whatsapp")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    @Value("${app.webhook.whatsapp.verify-token:}")
    private String verifyToken;

    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge
    ) {
        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            log.info("[WhatsApp Webhook] verification success");
            return ResponseEntity.ok(challenge);
        }

        log.warn("[WhatsApp Webhook] verification failed. mode={}", mode);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
    }

    @PostMapping
    public ResponseEntity<Void> receiveWebhook(@RequestBody(required = false) Map<String, Object> payload) {
        log.info("[WhatsApp Webhook] received payload={}", payload);
        return ResponseEntity.ok().build();
    }
}