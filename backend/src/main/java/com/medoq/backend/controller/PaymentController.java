package com.medoq.backend.controller;

import com.medoq.backend.dto.payment.InitiatePaymentRequest;
import com.medoq.backend.dto.payment.PaymentResponse;
import com.medoq.backend.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Context path: /api/v1
 *
 * POST /api/v1/payments/wave/initiate
 * POST /api/v1/payments/orange/initiate
 * GET  /api/v1/payments/{id}
 * GET  /api/v1/payments/reservation/{reservationId}
 */
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // ── POST /payments/wave/initiate ──────────────────────────────

    @PostMapping("/wave/initiate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PaymentResponse> initiateWave(
            @Valid @RequestBody InitiatePaymentRequest request,
            @AuthenticationPrincipal String userId) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(paymentService.initiateWave(
                    request.reservationId(), UUID.fromString(userId)));
    }

    // ── POST /payments/orange/initiate ────────────────────────────

    @PostMapping("/orange/initiate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PaymentResponse> initiateOrange(
            @Valid @RequestBody InitiatePaymentRequest request,
            @AuthenticationPrincipal String userId) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(paymentService.initiateOrange(
                    request.reservationId(), UUID.fromString(userId),
                    request.customerPhone()));
    }

    // ── GET /payments/{id} ────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PaymentResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.getById(id));
    }

    // ── GET /payments/reservation/{reservationId} ─────────────────

    @GetMapping("/reservation/{reservationId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PaymentResponse>> getByReservation(
            @PathVariable UUID reservationId) {
        return ResponseEntity.ok(paymentService.getByReservation(reservationId));
    }
}
