package com.medoq.backend.service.impl;

import com.medoq.backend.service.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Development/test SMS implementation — logs the message instead of sending it.
 * Active on profiles: default, dev, test
 *
 * To use a real provider in production, create an implementation annotated
 * with @Profile("prod") and inject your provider's SDK.
 */
@Service
@Profile({"default", "dev", "test"})
@Slf4j
public class MockSmsService implements SmsService {

    @Override
    public void send(String to, String message) {
        log.info("📱 [SMS MOCK] To: {} | Message: {}", to, message);
    }
}
