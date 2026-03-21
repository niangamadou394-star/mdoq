package com.medoq.backend.controller;

import com.medoq.backend.dto.reservation.*;
import com.medoq.backend.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Context path: /api/v1
 * Full paths:   /api/v1/reservations/**
 *
 * The @AuthenticationPrincipal injects the userId string set by JwtAuthFilter.
 * The role is extracted from the SecurityContext authority.
 */
@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    // ── POST /reservations ────────────────────────────────────────

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReservationResponse> create(
            @Valid @RequestBody CreateReservationRequest request,
            @AuthenticationPrincipal String userId) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(reservationService.create(request, UUID.fromString(userId)));
    }

    // ── GET /reservations/{id} ────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReservationResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal String userId,
            @CurrentRole String role) {

        return ResponseEntity.ok(
            reservationService.getById(id, UUID.fromString(userId), role));
    }

    // ── GET /reservations/patient/{patientId} ─────────────────────

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<ReservationResponse>> getByPatient(
            @PathVariable UUID patientId,
            @AuthenticationPrincipal String userId,
            @CurrentRole String role,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size,
            Sort.by(Sort.Direction.DESC, "createdAt"));

        return ResponseEntity.ok(
            reservationService.getByPatient(
                patientId, UUID.fromString(userId), role, pageable));
    }

    // ── GET /reservations/pharmacy/{pharmacyId} ───────────────────

    @GetMapping("/pharmacy/{pharmacyId}")
    @PreAuthorize("hasAnyRole('PHARMACY_OWNER','PHARMACY_STAFF','ADMIN')")
    public ResponseEntity<Page<ReservationResponse>> getByPharmacy(
            @PathVariable UUID pharmacyId,
            @AuthenticationPrincipal String userId,
            @CurrentRole String role,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size,
            Sort.by(Sort.Direction.DESC, "createdAt"));

        return ResponseEntity.ok(
            reservationService.getByPharmacy(
                pharmacyId, UUID.fromString(userId), role, pageable));
    }

    // ── PATCH /reservations/{id}/confirm ─────────────────────────

    @PatchMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('PHARMACY_OWNER','PHARMACY_STAFF','ADMIN')")
    public ResponseEntity<ReservationResponse> confirm(
            @PathVariable UUID id,
            @AuthenticationPrincipal String userId,
            @CurrentRole String role) {

        return ResponseEntity.ok(
            reservationService.confirm(id, UUID.fromString(userId), role));
    }

    // ── PATCH /reservations/{id}/cancel ──────────────────────────

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReservationResponse> cancel(
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid CancelRequest request,
            @AuthenticationPrincipal String userId,
            @CurrentRole String role) {

        return ResponseEntity.ok(
            reservationService.cancel(id, request, UUID.fromString(userId), role));
    }

    // ── PATCH /reservations/{id}/complete ────────────────────────

    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('PHARMACY_OWNER','PHARMACY_STAFF','ADMIN')")
    public ResponseEntity<ReservationResponse> complete(
            @PathVariable UUID id,
            @AuthenticationPrincipal String userId,
            @CurrentRole String role) {

        return ResponseEntity.ok(
            reservationService.complete(id, UUID.fromString(userId), role));
    }
}
