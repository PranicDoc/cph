package com.pranicdoc.cph.payments;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Public Razorpay webhook endpoint ({@code https://<cph-host>/api/webhooks/razorpay}). No auth
 * here: the body is passed byte-for-byte, with its {@code X-Razorpay-Signature}, to creditService,
 * which verifies the HMAC over the exact raw bytes.
 */
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class RazorpayWebhookProxyController {

    private final CreditServiceClient creditServiceClient;

    @PostMapping("/razorpay")
    public ResponseEntity<Void> razorpay(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        // required=false + explicit 400: a missing-header exception would hit the catch-all handler as a 500.
        if (signature == null || signature.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing X-Razorpay-Signature header");
        }
        creditServiceClient.forwardRazorpayWebhook(rawBody, signature);
        return ResponseEntity.ok().build();
    }
}
