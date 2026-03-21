package com.medoq.backend.dto.ussd;

import lombok.Data;

/**
 * Africa's Talking USSD callback payload.
 *
 * AT sends a POST with these form fields on every user interaction.
 * The {@code text} field accumulates all inputs separated by {@code *}.
 *
 * Example navigation:
 *   step 1 — text=""           → main menu
 *   step 2 — text="1"          → user chose option 1 (search)
 *   step 3 — text="1*paraceta" → user entered medication name
 *   step 4 — text="1*paraceta*2" → user chose pharmacy #2
 */
@Data
public class UssdRequest {
    private String sessionId;
    private String serviceCode;
    private String phoneNumber;
    private String text;           // Accumulated input, may be null on first hit
}
