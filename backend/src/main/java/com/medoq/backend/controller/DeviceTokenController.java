package com.medoq.backend.controller;

import com.medoq.backend.dto.notification.RegisterTokenRequest;
import com.medoq.backend.entity.DeviceToken;
import com.medoq.backend.entity.User;
import com.medoq.backend.repository.DeviceTokenRepository;
import com.medoq.backend.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/**
 * Manages FCM device tokens for a user.
 *
 * The mobile app calls POST /users/{id}/device-tokens after login
 * and DELETE when the user logs out or the token is rotated by FCM.
 */
@RestController
@RequestMapping("/users/{userId}/device-tokens")
@RequiredArgsConstructor
@Slf4j
public class DeviceTokenController {

    private final DeviceTokenRepository tokenRepo;
    private final UserRepository        userRepo;

    // ── POST /users/{userId}/device-tokens ───────────────────────────────────

    @PostMapping
    @PreAuthorize("authentication.name == #userId.toString() or hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> register(
            @PathVariable UUID userId,
            @Valid @RequestBody RegisterTokenRequest req) {

        User user = userRepo.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Upsert: if token already exists, just update metadata
        DeviceToken dt = tokenRepo.findByToken(req.getToken())
            .orElseGet(() -> DeviceToken.builder()
                .user(user)
                .token(req.getToken())
                .build());

        dt.setPlatform(req.getPlatform());
        dt.setAppVersion(req.getAppVersion());
        tokenRepo.save(dt);

        log.debug("Device token registered for user {} platform={}", userId, req.getPlatform());
        return ResponseEntity.ok(Map.of("registered", "true"));
    }

    // ── DELETE /users/{userId}/device-tokens/{token} ─────────────────────────

    @DeleteMapping("/{token}")
    @PreAuthorize("authentication.name == #userId.toString() or hasRole('ADMIN')")
    public ResponseEntity<Void> unregister(
            @PathVariable UUID userId,
            @PathVariable String token) {

        tokenRepo.deleteByToken(token);
        log.debug("Device token deleted for user {}", userId);
        return ResponseEntity.noContent().build();
    }
}
