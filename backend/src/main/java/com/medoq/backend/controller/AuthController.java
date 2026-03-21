package com.medoq.backend.controller;

import com.medoq.backend.dto.auth.*;
import com.medoq.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Authentication endpoints — all publicly accessible (no JWT required).
 * Context path is /api/v1 (set in application.yml).
 * Full paths: /api/v1/auth/**
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // ── POST /auth/register ───────────────────────────────────────

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    // ── POST /auth/register-pharmacy ──────────────────────────────

    @PostMapping("/register-pharmacy")
    public ResponseEntity<AuthResponse> registerPharmacy(
            @Valid @RequestBody RegisterPharmacyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerPharmacy(request));
    }

    // ── POST /auth/login ──────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // ── POST /auth/refresh ────────────────────────────────────────

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    // ── POST /auth/logout ─────────────────────────────────────────
    // Requires a valid access token in the Authorization header.

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader("Authorization") String authHeader,
            @AuthenticationPrincipal String userId) {

        String token = authHeader.startsWith("Bearer ")
                ? authHeader.substring(7)
                : authHeader;

        authService.logout(token, UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("message", "Logged out successfully."));
    }

    // ── POST /auth/forgot-password ────────────────────────────────

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        // Always return 200 to prevent phone enumeration
        return ResponseEntity.ok(Map.of(
            "message", "If this phone is registered, an OTP has been sent."));
    }

    // ── POST /auth/reset-password ─────────────────────────────────

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(Map.of("message", "Password reset successfully."));
    }
}
