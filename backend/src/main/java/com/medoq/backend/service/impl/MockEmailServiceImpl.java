package com.medoq.backend.service.impl;

import com.medoq.backend.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile({"default", "dev", "test"})
@Slf4j
public class MockEmailServiceImpl implements EmailService {

    @Override
    public void send(String to, String subject, String htmlBody,
                     String attachmentName, byte[] pdfBytes) {
        log.info("📧 [EMAIL MOCK] To: {} | Subject: {} | Attachment: {}",
            to, subject, attachmentName != null ? attachmentName : "none");
    }
}
