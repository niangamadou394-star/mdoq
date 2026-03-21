package com.medoq.backend.service;

import com.google.firebase.messaging.*;
import com.medoq.backend.entity.DeviceToken;
import com.medoq.backend.repository.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Sends push notifications via Firebase Cloud Messaging (FCM v1 API).
 *
 * Stale tokens that FCM reports as UNREGISTERED or INVALID_ARGUMENT are
 * automatically removed from the database to prevent accumulation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FcmService {

    private final FirebaseMessaging      fcm;
    private final DeviceTokenRepository  tokenRepo;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Send a notification to all registered devices of the given user.
     *
     * @return number of tokens successfully delivered to
     */
    @Transactional
    public int sendToUser(String userId, String title, String body, Map<String, String> data) {
        List<DeviceToken> tokens = tokenRepo.findByUserId(
            java.util.UUID.fromString(userId));

        if (tokens.isEmpty()) {
            log.debug("No FCM tokens for user {}", userId);
            return 0;
        }

        int success = 0;
        for (DeviceToken dt : tokens) {
            if (sendToToken(dt.getToken(), title, body, data)) {
                dt.setLastUsedAt(Instant.now());
                success++;
            }
        }
        return success;
    }

    /**
     * Send to a single FCM token.
     *
     * @return true if message was accepted by FCM
     */
    @Transactional
    public boolean sendToToken(String token, String title, String body,
                               Map<String, String> data) {
        Message.Builder builder = Message.builder()
            .setToken(token)
            .setNotification(Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build());

        if (data != null && !data.isEmpty()) {
            builder.putAllData(data);
        }

        // Android-specific: high priority for reservation alerts
        builder.setAndroidConfig(AndroidConfig.builder()
            .setPriority(AndroidConfig.Priority.HIGH)
            .build());

        // iOS: badge + sound
        builder.setApnsConfig(ApnsConfig.builder()
            .setAps(Aps.builder()
                .setBadge(1)
                .setSound("default")
                .build())
            .build());

        try {
            String messageId = fcm.send(builder.build());
            log.debug("FCM sent to token {}: messageId={}", abbreviate(token), messageId);
            return true;

        } catch (FirebaseMessagingException e) {
            MessagingErrorCode code = e.getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED
                || code == MessagingErrorCode.INVALID_ARGUMENT) {
                log.info("Removing stale FCM token {}: {}", abbreviate(token), code);
                tokenRepo.deleteByToken(token);
            } else {
                log.warn("FCM send failed for token {}: {} — {}",
                    abbreviate(token), code, e.getMessage());
            }
            return false;
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static String abbreviate(String token) {
        if (token == null || token.length() <= 12) return token;
        return token.substring(0, 8) + "..." + token.substring(token.length() - 4);
    }
}
