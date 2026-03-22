package com.medoq.backend.review;

import com.medoq.backend.dto.review.CreateReviewRequest;
import com.medoq.backend.dto.review.ReviewDto;
import com.medoq.backend.entity.Pharmacy;
import com.medoq.backend.entity.Reservation;
import com.medoq.backend.entity.Review;
import com.medoq.backend.entity.User;
import com.medoq.backend.exception.BusinessException;
import com.medoq.backend.exception.ResourceNotFoundException;
import com.medoq.backend.repository.PharmacyRepository;
import com.medoq.backend.repository.ReservationRepository;
import com.medoq.backend.repository.ReviewRepository;
import com.medoq.backend.repository.UserRepository;
import com.medoq.backend.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock ReviewRepository      reviewRepo;
    @Mock ReservationRepository reservationRepo;
    @Mock PharmacyRepository    pharmacyRepo;
    @Mock UserRepository        userRepo;

    @InjectMocks
    ReviewService reviewService;

    private UUID customerId;
    private UUID reservationId;
    private UUID pharmacyId;
    private User customer;
    private Pharmacy pharmacy;
    private Reservation completedReservation;

    @BeforeEach
    void setUp() {
        customerId    = UUID.randomUUID();
        reservationId = UUID.randomUUID();
        pharmacyId    = UUID.randomUUID();

        customer = User.builder()
            .id(customerId)
            .firstName("Amadou").lastName("Niang")
            .phone("+221777654321")
            .role(User.Role.CUSTOMER)
            .status(User.Status.ACTIVE)
            .build();

        pharmacy = Pharmacy.builder()
            .id(pharmacyId)
            .name("Pharmacie du Plateau")
            .licenseNumber("PH-2024-001")
            .city("Dakar").region("Dakar")
            .phone("+221338201234")
            .status(Pharmacy.Status.ACTIVE)
            .rating(BigDecimal.valueOf(4.0))
            .reviewCount(5)
            .owner(customer)
            .build();

        completedReservation = Reservation.builder()
            .id(reservationId)
            .customer(customer)
            .pharmacy(pharmacy)
            .status(Reservation.Status.COMPLETED)
            .build();
    }

    // ── 1. Non-COMPLETED reservation is rejected ─────────────────

    @Test
    @DisplayName("submitReview throws BusinessException for PENDING reservation")
    void submitReview_pendingReservation_throws() {
        Reservation pending = Reservation.builder()
            .id(reservationId)
            .customer(customer)
            .pharmacy(pharmacy)
            .status(Reservation.Status.PENDING)
            .build();

        when(reservationRepo.findById(reservationId)).thenReturn(Optional.of(pending));

        CreateReviewRequest req = new CreateReviewRequest(reservationId, (short) 4, "Super pharmacie");

        assertThatThrownBy(() -> reviewService.submitReview(customerId, req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("COMPLETÉE");
    }

    @Test
    @DisplayName("submitReview throws BusinessException for CONFIRMED (not yet completed) reservation")
    void submitReview_confirmedReservation_throws() {
        Reservation confirmed = Reservation.builder()
            .id(reservationId)
            .customer(customer)
            .pharmacy(pharmacy)
            .status(Reservation.Status.CONFIRMED)
            .build();

        when(reservationRepo.findById(reservationId)).thenReturn(Optional.of(confirmed));

        CreateReviewRequest req = new CreateReviewRequest(reservationId, (short) 5, null);

        assertThatThrownBy(() -> reviewService.submitReview(customerId, req))
            .isInstanceOf(BusinessException.class);
    }

    // ── 2. Cannot review same reservation twice ───────────────────

    @Test
    @DisplayName("submitReview throws BusinessException when review already exists for reservation")
    void submitReview_duplicateReservation_throws() {
        when(reservationRepo.findById(reservationId)).thenReturn(Optional.of(completedReservation));
        when(reviewRepo.existsByReservationId(reservationId)).thenReturn(true);

        CreateReviewRequest req = new CreateReviewRequest(reservationId, (short) 5, "Excellent");

        assertThatThrownBy(() -> reviewService.submitReview(customerId, req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("déjà");
    }

    // ── 3. Successful review — isVerified set to true ────────────

    @Test
    @DisplayName("submitReview saves review with isVerified=true for COMPLETED reservation")
    void submitReview_completed_savesVerifiedReview() {
        when(reservationRepo.findById(reservationId)).thenReturn(Optional.of(completedReservation));
        when(reviewRepo.existsByReservationId(reservationId)).thenReturn(false);

        Review saved = Review.builder()
            .id(UUID.randomUUID())
            .reservation(completedReservation)
            .pharmacy(pharmacy)
            .customer(customer)
            .rating((short) 5)
            .comment("Très bonne pharmacie")
            .isVerified(true)
            .isVisible(true)
            .build();
        when(reviewRepo.save(any(Review.class))).thenReturn(saved);

        CreateReviewRequest req = new CreateReviewRequest(reservationId, (short) 5, "Très bonne pharmacie");
        ReviewDto result = reviewService.submitReview(customerId, req);

        assertThat(result.rating()).isEqualTo((short) 5);
        assertThat(result.isVerified()).isTrue();
        assertThat(result.comment()).isEqualTo("Très bonne pharmacie");
        assertThat(result.pharmacyId()).isEqualTo(pharmacyId);

        verify(reviewRepo).save(argThat(r ->
            r.isVerified() &&
            r.isVisible() &&
            r.getReservation().getId().equals(reservationId)
        ));
    }

    // ── 4. Wrong customer cannot review ──────────────────────────

    @Test
    @DisplayName("submitReview throws BusinessException when customer doesn't own the reservation")
    void submitReview_wrongCustomer_throws() {
        UUID anotherCustomerId = UUID.randomUUID();
        when(reservationRepo.findById(reservationId)).thenReturn(Optional.of(completedReservation));

        CreateReviewRequest req = new CreateReviewRequest(reservationId, (short) 4, null);

        assertThatThrownBy(() -> reviewService.submitReview(anotherCustomerId, req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("propres réservations");
    }

    // ── 5. Unknown reservation throws ResourceNotFoundException ──

    @Test
    @DisplayName("submitReview throws ResourceNotFoundException for unknown reservationId")
    void submitReview_unknownReservation_throws() {
        when(reservationRepo.findById(any())).thenReturn(Optional.empty());

        CreateReviewRequest req = new CreateReviewRequest(UUID.randomUUID(), (short) 3, null);

        assertThatThrownBy(() -> reviewService.submitReview(customerId, req))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── 6. ratingSummary returns correct average ─────────────────

    @Test
    @DisplayName("ratingSummary returns correct average and count")
    void ratingSummary_returnsCorrectData() {
        when(pharmacyRepo.findById(pharmacyId)).thenReturn(Optional.of(pharmacy));
        when(reviewRepo.averageRatingByPharmacyId(pharmacyId)).thenReturn(4.67);
        when(reviewRepo.countVisibleByPharmacyId(pharmacyId)).thenReturn(6L);

        var summary = reviewService.ratingSummary(pharmacyId);

        assertThat(summary.averageRating()).isEqualByComparingTo(new BigDecimal("4.67"));
        assertThat(summary.reviewCount()).isEqualTo(6L);
        assertThat(summary.pharmacyName()).isEqualTo("Pharmacie du Plateau");
    }

    @Test
    @DisplayName("ratingSummary returns zero when pharmacy has no reviews")
    void ratingSummary_noReviews_returnsZero() {
        when(pharmacyRepo.findById(pharmacyId)).thenReturn(Optional.of(pharmacy));
        when(reviewRepo.averageRatingByPharmacyId(pharmacyId)).thenReturn(null);
        when(reviewRepo.countVisibleByPharmacyId(pharmacyId)).thenReturn(0L);

        var summary = reviewService.ratingSummary(pharmacyId);

        assertThat(summary.averageRating()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.reviewCount()).isZero();
    }
}
