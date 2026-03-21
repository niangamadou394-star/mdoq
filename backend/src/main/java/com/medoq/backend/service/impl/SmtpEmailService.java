package com.medoq.backend.service.impl;

import com.medoq.backend.config.PaymentProperties;
import com.medoq.backend.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Profile({"prod", "staging"})
@Slf4j
@RequiredArgsConstructor
public class SmtpEmailService implements EmailService {

    private final JavaMailSender     mailSender;
    private final PaymentProperties  props;

    @Override
    @Async
    public void send(String to, String subject, String htmlBody,
                     String attachmentName, byte[] pdfBytes) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, pdfBytes != null, "UTF-8");

            helper.setFrom(props.getEmail().getFrom(), props.getEmail().getFromName());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            if (pdfBytes != null && attachmentName != null) {
                helper.addAttachment(attachmentName,
                    () -> new java.io.ByteArrayInputStream(pdfBytes),
                    "application/pdf");
            }

            mailSender.send(msg);
            log.info("Email sent to {} — subject: {}", to, subject);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
