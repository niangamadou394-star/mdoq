package com.medoq.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Initialises the Firebase Admin SDK.
 *
 * Priority order for credentials:
 *  1. {@code medoq.fcm.service-account-path} property (explicit file path)
 *  2. {@code GOOGLE_APPLICATION_CREDENTIALS} environment variable (standard ADC)
 *  3. Metadata server (when running on GCP / Cloud Run)
 *
 * In local dev, point {@code FCM_SERVICE_ACCOUNT_PATH} to the downloaded
 * service-account JSON from the Firebase console.
 */
@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${medoq.fcm.service-account-path:}")
    private String serviceAccountPath;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            GoogleCredentials credentials = resolveCredentials();
            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build();
            FirebaseApp app = FirebaseApp.initializeApp(options);
            log.info("Firebase Admin SDK initialised (app={})", app.getName());
            return app;
        }
        return FirebaseApp.getInstance();
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp app) {
        return FirebaseMessaging.getInstance(app);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private GoogleCredentials resolveCredentials() throws IOException {
        if (StringUtils.hasText(serviceAccountPath)) {
            log.debug("FCM: loading credentials from {}", serviceAccountPath);
            try (InputStream stream = new FileInputStream(serviceAccountPath)) {
                return GoogleCredentials.fromStream(stream);
            }
        }
        // Falls back to GOOGLE_APPLICATION_CREDENTIALS env var or GCP metadata
        log.debug("FCM: using Application Default Credentials");
        return GoogleCredentials.getApplicationDefault();
    }
}
