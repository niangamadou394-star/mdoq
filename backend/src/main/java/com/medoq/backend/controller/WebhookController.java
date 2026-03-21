package com.medoq.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medoq.backend.config.PaymentProperties;
import com.medoq.backend.dto.payment.OrangeCallbackPayload;
import com.medoq.backend.dto.payment.WaveWebhookEvent;
import com.medoq.backend.service.PaymentService;
import com.medoq.backend.util.HmacSignatureUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Receives webhooks from Wave and Orange Money.
 *
 * These endpoints are intentionally PUBLIC (no JWT auth) because
 * they are called by external providers. Security is provided by:
 *  - Wave:         HMAC-SHA256 signature on X-Wave-Signature header
 *  - Orange Money: notification token in payload (notif_token field)
 */
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final PaymentService    paymentService;
    private final PaymentProperties props;
    private final ObjectMapper      objectMapper;

    // ── POST /webhooks/wave ───────────────────────────────────────

    @PostMapping("/wave")
    public ResponseEntity<Map<String, String>> waveWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Wave-Signature", required = false) String signature,
            HttpServletRequest request) {

        log.debug("Wave webhook received from {}", request.getRemoteAddr());

        // 1 — Verify HMAC signature
        String secret = props.getPayment().getWave().getWebhookSecret();
        if (signature == null || !HmacSignatureUtil.verify(rawBody, secret, signature)) {
            log.warn("Invalid Wave webhook signature from {}", request.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid signature"));
        }

        // 2 — Parse and process
        try {
            WaveWebhookEvent event = objectMapper.readValue(rawBody, WaveWebhookEvent.class);
            paymentService.processWaveWebhook(event, rawBody, request.getRemoteAddr());
            return ResponseEntity.ok(Map.of("received", "true"));
        } catch (Exception e) {
            log.error("Failed to process Wave webhook: {}", e.getMessage());
            // Return 200 to prevent Wave from retrying a permanently invalid event
            return ResponseEntity.ok(Map.of("error", "Processing failed"));
        }
    }

    // ── POST /webhooks/orange-money ───────────────────────────────

    @PostMapping("/orange-money")
    public ResponseEntity<Map<String, String>> orangeMoneyWebhook(
            @RequestBody OrangeCallbackPayload payload,
            HttpServletRequest request) {

        log.debug("Orange Money callback received from {}", request.getRemoteAddr());

        try {
            paymentService.processOrangeCallback(payload, request.getRemoteAddr());
            return ResponseEntity.ok(Map.of("received", "true"));
        } catch (Exception e) {
            log.error("Failed to process Orange Money callback: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
