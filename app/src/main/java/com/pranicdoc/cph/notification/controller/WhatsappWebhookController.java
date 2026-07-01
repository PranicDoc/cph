package com.pranicdoc.cph.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pranicdoc.cph.notification.dto.WhatsappWebhookPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@RestController
@RequestMapping("/api/v1/webhooks/whatsapp")
public class WhatsappWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsappWebhookController.class);

    @Value("${app.whatsapp.webhook-verify-token}")
    private String verifyToken;

    @Value("${app.whatsapp.app-secret:}")
    private String appSecret;

    private final ObjectMapper objectMapper;

    public WhatsappWebhookController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Meta calls this GET endpoint once when you register the webhook in the dashboard.
     * Must echo back hub.challenge as plain text with 200 to confirm ownership.
     */
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {

        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            log.info("WhatsApp webhook verified successfully");
            return ResponseEntity.ok(challenge);
        }

        log.warn("WhatsApp webhook verification failed — token mismatch or unexpected mode '{}'", mode);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification failed");
    }

    /**
     * Meta POSTs all incoming messages and delivery status updates here.
     * Must respond 200 quickly; heavy processing belongs in a background task.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> handleEvent(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {

        if (!isSignatureValid(rawBody, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            WhatsappWebhookPayload payload = objectMapper.readValue(rawBody, WhatsappWebhookPayload.class);
            processPayload(payload);
        } catch (Exception e) {
            log.error("Failed to parse WhatsApp webhook payload: {}", e.getMessage());
        }

        // Always return 200 — Meta retries on non-2xx, flooding the endpoint
        return ResponseEntity.ok().build();
    }

    private void processPayload(WhatsappWebhookPayload payload) {
        if (payload.entry() == null) return;

        for (WhatsappWebhookPayload.Entry entry : payload.entry()) {
            if (entry.changes() == null) continue;

            for (WhatsappWebhookPayload.Change change : entry.changes()) {
                if (!"messages".equals(change.field()) || change.value() == null) continue;

                WhatsappWebhookPayload.Value value = change.value();

                List<WhatsappWebhookPayload.IncomingMessage> messages = value.messages();
                if (messages != null) {
                    for (WhatsappWebhookPayload.IncomingMessage msg : messages) {
                        log.info("Incoming WhatsApp message — from: {}, type: {}, id: {}",
                                msg.from(), msg.type(), msg.id());
                    }
                }

                List<WhatsappWebhookPayload.StatusUpdate> statuses = value.statuses();
                if (statuses != null) {
                    for (WhatsappWebhookPayload.StatusUpdate status : statuses) {
                        log.info("WhatsApp delivery status — wamid: {}, status: {}, recipient: {}",
                                status.id(), status.status(), status.recipientId());
                        if ("failed".equals(status.status())) {
                            log.warn("WhatsApp delivery FAILED for wamid: {}, recipient: {}",
                                    status.id(), status.recipientId());
                        }
                    }
                }
            }
        }
    }

    // Returns true when signature is valid, or when app-secret is not configured (skip verification).
    private boolean isSignatureValid(byte[] rawBody, String signature) {
        if (appSecret == null || appSecret.isBlank() || appSecret.startsWith("placeholder")) {
            log.debug("WHATSAPP_APP_SECRET not configured — skipping signature verification");
            return true;
        }
        if (signature == null || !signature.startsWith("sha256=")) {
            log.warn("Missing or malformed X-Hub-Signature-256 header");
            return false;
        }
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String computed = "sha256=" + HexFormat.of().formatHex(hmac.doFinal(rawBody));
            return computed.equals(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Signature verification error: {}", e.getMessage());
            return false;
        }
    }
}
