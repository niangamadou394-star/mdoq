package com.medoq.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Sends SMS via Africa's Talking REST API.
 *
 * Used as a fallback when the patient has no smartphone / FCM token,
 * or as the primary channel for USSD-only users.
 *
 * Docs: https://developers.africastalking.com/docs/sms/sending
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AtSmsService {

    private static final String AT_SMS_URL =
        "https://api.africastalking.com/version1/messaging";

    private final RestTemplate restTemplate;

    @Value("${medoq.at.api-key}")
    private String apiKey;

    @Value("${medoq.at.username}")
    private String username;

    @Value("${medoq.at.sender-id:Medoq}")
    private String senderId;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Send an SMS to one phone number.
     *
     * @param phoneNumber E.164 format, e.g. +221771234567
     * @param message     ≤160 chars for single segment
     * @return true if AT accepted the message
     */
    public boolean send(String phoneNumber, String message) {
        return sendBulk(List.of(phoneNumber), message);
    }

    /**
     * Send the same message to multiple recipients in one API call.
     */
    public boolean sendBulk(List<String> phoneNumbers, String message) {
        if (phoneNumbers == null || phoneNumbers.isEmpty()) return false;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.set("apiKey", apiKey);

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("username", username);
            form.add("to",       String.join(",", phoneNumbers));
            form.add("message",  message);
            if (senderId != null && !senderId.isBlank()) {
                form.add("from", senderId);
            }

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                AT_SMS_URL, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("AT SMS accepted for {} recipient(s)", phoneNumbers.size());
                return true;
            } else {
                log.warn("AT SMS rejected: HTTP {} — {}", response.getStatusCode(), response.getBody());
                return false;
            }

        } catch (Exception e) {
            log.error("AT SMS send failed to {}: {}", phoneNumbers, e.getMessage());
            return false;
        }
    }
}
