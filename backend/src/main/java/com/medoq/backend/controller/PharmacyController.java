package com.medoq.backend.controller;

import com.medoq.backend.dto.search.PharmacyNearbyDto;
import com.medoq.backend.service.MedicationSearchService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Context path: /api/v1
 * Full paths:
 *   GET /api/v1/pharmacies/nearby?lat=...&lng=...&medication_id=...&radius=...
 */
@RestController
@RequestMapping("/pharmacies")
@RequiredArgsConstructor
@Validated
public class PharmacyController {

    private final MedicationSearchService searchService;

    // ── GET /pharmacies/nearby ────────────────────────────────────

    @GetMapping("/nearby")
    public ResponseEntity<List<PharmacyNearbyDto>> nearby(

            @RequestParam
            @NotNull(message = "lat is required")
            @DecimalMin(value = "-90.0",  message = "Latitude must be >= -90")
            @DecimalMax(value = "90.0",   message = "Latitude must be <= 90")
            Double lat,

            @RequestParam
            @NotNull(message = "lng is required")
            @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
            @DecimalMax(value = "180.0",  message = "Longitude must be <= 180")
            Double lng,

            @RequestParam(name = "medication_id", required = false)
            UUID medicationId,

            @RequestParam(required = false, defaultValue = "5.0")
            @DecimalMin(value = "0.5",    message = "Radius must be >= 0.5 km")
            @DecimalMax(value = "50.0",   message = "Radius must be <= 50 km")
            Double radius) {

        return ResponseEntity.ok(
            searchService.findNearbyPharmacies(lat, lng, medicationId, radius));
    }
}
