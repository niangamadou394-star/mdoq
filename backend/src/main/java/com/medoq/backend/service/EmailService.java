package com.medoq.backend.service;

/**
 * Abstraction over the email provider (SMTP, SendGrid, etc.).
 */
public interface EmailService {

    /**
     * Sends an email with an optional PDF attachment.
     *
     * @param to          recipient address
     * @param subject     email subject
     * @param htmlBody    HTML body content
     * @param attachmentName  filename for the PDF attachment (null if none)
     * @param pdfBytes    PDF bytes (null if no attachment)
     */
    void send(String to, String subject, String htmlBody,
              String attachmentName, byte[] pdfBytes);
}
