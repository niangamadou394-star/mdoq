package com.medoq.backend.service;

import com.medoq.backend.dto.admin.PageResponse;
import com.medoq.backend.dto.review.CreateReviewRequest;
import com.medoq.backend.dto.review.RatingSummaryDto;
import com.medoq.backend.dto.review.ReviewDto;
import com.medoq.backend.entity.Pharmacy;
import com.medoq.backend.entity.Reservation;
import com.medoq.backend.entity.Review;
import com.medoq.backend.exception.BusinessException;
import com.medoq.backend.exception.ResourceNotFoundException;
import com.medoq.backend.repository.PharmacyRepository;
import com.medoq.backend.repository.ReservationRepository;
import com.medoq.backend.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository      reviewRepo;
    private final ReservationRepository reservationRepo;
    private final PharmacyRepository    pharmacyRepo;

    // ── Submit review ────────────────────────────────────────────

    @Transactional
    public ReviewDto submitReview(UUID customerId, CreateReviewRequest req) {
        Reservation reservation = reservationRepo.findById(req.reservationId())
            .orElseThrow(() -> new ResourceNotFoundException("Reservation", req.reservationId()));

        // Only the reservation owner can review
        if (!reservation.getCustomer().getId().equals(customerId)) {
            throw new BusinessException("Vous ne pouvez noter que vos propres réservations");
        }

        // Only COMPLETED reservations qualify
        if (reservation.getStatus() != Reservation.Status.COMPLETED) {
            throw new BusinessException(
                "Vous ne pouvez laisser un avis que pour une réservation COMPLETÉE");
        }

        // One review per reservation
        if (reviewRepo.existsByReservationId(req.reservationId())) {
            throw new BusinessException(
                "Vous avez déjà laissé un avis pour cette réservation");
        }

        User customer = reservation.getCustomer();
        Pharmacy pharmacy = reservation.getPharmacy();

        Review review = Review.builder()
            .reservation(reservation)
            .pharmacy(pharmacy)
            .customer(customer)
            .rating(req.rating())
            .comment(req.comment())
            .isVerified(true)   // reservation is COMPLETED → purchase verified
            .isVisible(true)
            .build();

        review = reviewRepo.save(review);
        log.info("Review {} created by customer {} for pharmacy {}", review.getId(), customerId, pharmacy.getId());
        return ReviewDto.from(review);
    }

    // ── List pharmacy reviews ────────────────────────────────────

    public PageResponse<ReviewDto> listPharmacyReviews(UUID pharmacyId, Pageable pageable) {
        if (!pharmacyRepo.existsById(pharmacyId)) {
            throw new ResourceNotFoundException("Pharmacy", pharmacyId);
        }
        Page<Review> page = reviewRepo.findVisibleByPharmacyId(pharmacyId, pageable);
        return PageResponse.of(page.map(ReviewDto::from));
    }

    // ── Rating summary ───────────────────────────────────────────

    public RatingSummaryDto ratingSummary(UUID pharmacyId) {
        Pharmacy pharmacy = pharmacyRepo.findById(pharmacyId)
            .orElseThrow(() -> new ResourceNotFoundException("Pharmacy", pharmacyId));

        Double avg = reviewRepo.averageRatingByPharmacyId(pharmacyId);
        long count = reviewRepo.countVisibleByPharmacyId(pharmacyId);

        BigDecimal average = avg != null
            ? BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        return new RatingSummaryDto(pharmacyId, pharmacy.getName(), average, count);
    }

    // ── Admin: hide review ───────────────────────────────────────

    @Transactional
    public void adminDeleteReview(UUID reviewId, String adminUserId) {
        reviewRepo.findById(reviewId)
            .orElseThrow(() -> new ResourceNotFoundException("Review", reviewId));
        reviewRepo.hideById(reviewId);
        log.info("Admin {} hid review {}", adminUserId, reviewId);
    }
}
