package com.medoq.backend.controller;

import com.medoq.backend.dto.admin.PageResponse;
import com.medoq.backend.dto.review.CreateReviewRequest;
import com.medoq.backend.dto.review.RatingSummaryDto;
import com.medoq.backend.dto.review.ReviewDto;
import com.medoq.backend.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Reviews API
 *
 * POST   /api/v1/reviews                         — patient submits a review
 * GET    /api/v1/reviews/pharmacy/{pharmacyId}   — paginated list of reviews
 * GET    /api/v1/pharmacies/{id}/rating          — rating summary
 * DELETE /api/v1/admin/reviews/{id}              — admin hides abusive review
 */
@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    // ── Customer: submit review ──────────────────────────────────

    /**
     * POST /reviews
     * Body: { reservationId, rating (1-5), comment (optional, ≤500 chars) }
     * Requires COMPLETED reservation owned by the authenticated customer.
     */
    @PostMapping("/reviews")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ReviewDto> submitReview(
            @Valid @RequestBody CreateReviewRequest request,
            @AuthenticationPrincipal String customerId) {
        ReviewDto dto = reviewService.submitReview(UUID.fromString(customerId), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    // ── Public: list pharmacy reviews ────────────────────────────

    /**
     * GET /reviews/pharmacy/{pharmacyId}?page=0&size=10
     */
    @GetMapping("/reviews/pharmacy/{pharmacyId}")
    public ResponseEntity<PageResponse<ReviewDto>> listPharmacyReviews(
            @PathVariable UUID pharmacyId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(
            reviewService.listPharmacyReviews(pharmacyId,
                PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }

    // ── Public: rating summary ───────────────────────────────────

    /**
     * GET /pharmacies/{id}/rating
     */
    @GetMapping("/pharmacies/{id}/rating")
    public ResponseEntity<RatingSummaryDto> ratingSummary(@PathVariable UUID id) {
        return ResponseEntity.ok(reviewService.ratingSummary(id));
    }

    // ── Admin: hide abusive review ───────────────────────────────

    /**
     * DELETE /admin/reviews/{id}
     * Soft-hides the review (is_visible = false) to preserve the audit trail.
     */
    @DeleteMapping("/admin/reviews/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> adminDeleteReview(
            @PathVariable UUID id,
            @AuthenticationPrincipal String adminId) {
        reviewService.adminDeleteReview(id, adminId);
        return ResponseEntity.noContent().build();
    }
}
