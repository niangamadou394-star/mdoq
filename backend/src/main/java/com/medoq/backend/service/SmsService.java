package com.medoq.backend.service;

/**
 * Abstraction over the SMS provider (Orange Senegal, Twilio, etc.).
 * Swap the implementation without touching business logic.
 */
public interface SmsService {

    /**
     * Sends a single SMS message.
     *
     * @param to      recipient phone in E.164 format (+221XXXXXXXXX)
     * @param message plain-text message body
     */
    void send(String to, String message);
}
