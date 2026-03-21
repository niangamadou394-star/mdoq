package com.medoq.backend.controller;

import com.medoq.backend.dto.search.MedicationDetailDto;
import com.medoq.backend.dto.search.PopularMedicationDto;
import com.medoq.backend.dto.search.SearchResponse;
import com.medoq.backend.service.MedicationSearchService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Context path: /api/v1
 * Full paths:
 *   GET /api/v1/medications/popular
 *   GET /api/v1/medications/search?q=...&lat=...&lng=...&radius=...
 *   GET /api/v1/medications/{id}
 *
 * NOTE: /popular and /search are declared before /{id} so Spring MVC
 * does not try to parse "popular" or "search" as a UUID.
 */
@RestController
@RequestMapping("/medications")
@RequiredArgsConstructor
@Validated
public class MedicationController {

    private final MedicationSearchService searchService;

    // ── GET /medications/popular ──────────────────────────────────

    @GetMapping("/popular")
    public ResponseEntity<List<PopularMedicationDto>> popular() {
        return ResponseEntity.ok(searchService.getPopular());
    }

    // ── GET /medications/search ───────────────────────────────────

    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(
            @RequestParam
            @NotBlank(message = "Search query must not be blank")
            @Size(min = 2, max = 100, message = "Query must be 2-100 characters")
            String q,

            @RequestParam(required = false)
            @DecimalMin(value = "-90.0",  message = "Latitude must be >= -90")
            @DecimalMax(value = "90.0",   message = "Latitude must be <= 90")
            Double lat,

            @RequestParam(required = false)
            @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
            @DecimalMax(value = "180.0",  message = "Longitude must be <= 180")
            Double lng,

            @RequestParam(required = false, defaultValue = "5.0")
            @DecimalMin(value = "0.5",    message = "Radius must be >= 0.5 km")
            @DecimalMax(value = "50.0",   message = "Radius must be <= 50 km")
            Double radius) {

        // Require both lat and lng together
        if ((lat == null) != (lng == null)) {
            throw new com.medoq.backend.exception.BusinessException(
                "Both 'lat' and 'lng' must be provided together.");
        }

        return ResponseEntity.ok(searchService.search(q, lat, lng, radius));
    }

    // ── GET /medications/{id} ─────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<MedicationDetailDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(searchService.getById(id));
    }
}
