package com.medoq.backend.reservation;

import com.medoq.backend.dto.reservation.*;
import com.medoq.backend.entity.*;
import com.medoq.backend.exception.BusinessException;
import com.medoq.backend.repository.*;
import com.medoq.backend.service.NotificationService;
import com.medoq.backend.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock ReservationRepository     reservationRepository;
    @Mock PharmacyRepository        pharmacyRepository;
    @Mock PharmacyUserRepository    pharmacyUserRepository;
    @Mock MedicationRepository      medicationRepository;
    @Mock PharmacyStockRepository   stockRepository;
    @Mock UserRepository            userRepository;
    @Mock NotificationService       notificationService;

    @InjectMocks ReservationService reservationService;

    // ── Fixtures ──────────────────────────────────────────────────

    private UUID customerId, pharmacyId, medId, stockId, reservationId;
    private User customer;
    private Pharmacy pharmacy;
    private Medication medication;
    private PharmacyStock stock;
    private Reservation pendingReservation;

    @BeforeEach
    void setUp() {
        customerId    = UUID.randomUUID();
        pharmacyId    = UUID.randomUUID();
        medId         = UUID.randomUUID();
        stockId       = UUID.randomUUID();
        reservationId = UUID.randomUUID();

        customer = User.builder()
                .id(customerId).phone("+221771234567")
                .firstName("Amadou").lastName("Diallo")
                .role(User.Role.CUSTOMER).status(User.Status.ACTIVE)
                .build();

        pharmacy = Pharmacy.builder()
                .id(pharmacyId).name("Pharmacie du Plateau")
                .address("Rue de Thiong").city("Dakar").phone("+221338210001")
                .status(Pharmacy.Status.ACTIVE).owner(customer)
                .build();

        medication = Medication.builder()
                .id(medId).name("Paracétamol 500mg").strength("500mg")
                .requiresPrescription(false)
                .build();

        stock = PharmacyStock.builder()
                .id(stockId).pharmacy(pharmacy).medication(medication)
                .quantity(50).unitPrice(BigDecimal.valueOf(500))
                .reorderLevel(10).isAvailable(true)
                .build();

        ReservationItem item = ReservationItem.builder()
                .medication(medication).quantity(2)
                .unitPrice(BigDecimal.valueOf(500))
                .build();

        pendingReservation = Reservation.builder()
                .id(reservationId)
                .reference("MQ-240321-01000")
                .customer(customer)
                .pharmacy(pharmacy)
                .status(Reservation.Status.PENDING)
                .totalAmount(BigDecimal.valueOf(1000))
                .expiresAt(Instant.now().plus(2, ChronoUnit.HOURS))
                .items(new ArrayList<>(List.of(item)))
                .build();
        item.setReservation(pendingReservation);
    }

    // ── create ────────────────────────────────────────────────────

    @Test
    void create_success_computesTotalFromStockPrice() {
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(pharmacyRepository.findById(pharmacyId)).thenReturn(Optional.of(pharmacy));
        when(medicationRepository.findById(medId)).thenReturn(Optional.of(medication));
        when(stockRepository.findByPharmacyIdAndMedicationId(pharmacyId, medId))
                .thenReturn(Optional.of(stock));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            r.setId(reservationId);
            r.setReference("MQ-240321-01000");
            return r;
        });

        var req = new CreateReservationRequest(
            pharmacyId,
            List.of(new ReservationItemRequest(medId, 3)),
            null, "Urgent"
        );

        ReservationResponse resp = reservationService.create(req, customerId);

        assertThat(resp.totalAmount()).isEqualByComparingTo("1500.00"); // 500 * 3
        assertThat(resp.status()).isEqualTo(Reservation.Status.PENDING);
    }

    @Test
    void create_pharmacyInactive_throws() {
        pharmacy.setStatus(Pharmacy.Status.INACTIVE);
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(pharmacyRepository.findById(pharmacyId)).thenReturn(Optional.of(pharmacy));

        var req = new CreateReservationRequest(pharmacyId,
            List.of(new ReservationItemRequest(medId, 1)), null, null);

        assertThatThrownBy(() -> reservationService.create(req, customerId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not currently accepting");
    }

    @Test
    void create_insufficientStock_throws() {
        stock.setQuantity(1);
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(pharmacyRepository.findById(pharmacyId)).thenReturn(Optional.of(pharmacy));
        when(medicationRepository.findById(medId)).thenReturn(Optional.of(medication));
        when(stockRepository.findByPharmacyIdAndMedicationId(pharmacyId, medId))
                .thenReturn(Optional.of(stock));

        var req = new CreateReservationRequest(pharmacyId,
            List.of(new ReservationItemRequest(medId, 5)), null, null);

        assertThatThrownBy(() -> reservationService.create(req, customerId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient stock");
    }

    // ── confirm ───────────────────────────────────────────────────

    @Test
    void confirm_success_decrementsStockAndNotifies() {
        when(reservationRepository.findByIdWithDetails(reservationId))
                .thenReturn(Optional.of(pendingReservation));
        when(pharmacyUserRepository.existsByPharmacyIdAndUserId(pharmacyId, customerId))
                .thenReturn(true);
        when(stockRepository.decrementStock(pharmacyId, medId, 2)).thenReturn(1);
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReservationResponse resp = reservationService.confirm(reservationId, customerId, "PHARMACY_STAFF");

        assertThat(resp.status()).isEqualTo(Reservation.Status.CONFIRMED);
        verify(stockRepository).decrementStock(pharmacyId, medId, 2);
        verify(notificationService).sendReservationConfirmed(any());
    }

    @Test
    void confirm_expiredReservation_throws() {
        pendingReservation.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(reservationRepository.findByIdWithDetails(reservationId))
                .thenReturn(Optional.of(pendingReservation));
        when(pharmacyUserRepository.existsByPharmacyIdAndUserId(any(), any())).thenReturn(true);
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() ->
            reservationService.confirm(reservationId, customerId, "PHARMACY_STAFF"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void confirm_insufficientStockAtTime_throws() {
        when(reservationRepository.findByIdWithDetails(reservationId))
                .thenReturn(Optional.of(pendingReservation));
        when(pharmacyUserRepository.existsByPharmacyIdAndUserId(any(), any())).thenReturn(true);
        when(stockRepository.decrementStock(any(), any(), anyInt())).thenReturn(0);

        assertThatThrownBy(() ->
            reservationService.confirm(reservationId, customerId, "PHARMACY_STAFF"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient stock");
    }

    // ── cancel ────────────────────────────────────────────────────

    @Test
    void cancel_fromPending_noStockRestore() {
        when(reservationRepository.findByIdWithDetails(reservationId))
                .thenReturn(Optional.of(pendingReservation));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReservationResponse resp = reservationService.cancel(
            reservationId, new CancelRequest("changed mind"), customerId, "CUSTOMER");

        assertThat(resp.status()).isEqualTo(Reservation.Status.CANCELLED);
        verify(stockRepository, never()).incrementStock(any(), any(), anyInt());
        verify(notificationService).sendReservationCancelled(any());
    }

    @Test
    void cancel_fromConfirmed_restoresStock() {
        pendingReservation.setStatus(Reservation.Status.CONFIRMED);
        when(reservationRepository.findByIdWithDetails(reservationId))
                .thenReturn(Optional.of(pendingReservation));
        when(stockRepository.incrementStock(pharmacyId, medId, 2)).thenReturn(1);
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        reservationService.cancel(reservationId, null, customerId, "CUSTOMER");

        verify(stockRepository).incrementStock(pharmacyId, medId, 2);
    }

    @Test
    void cancel_alreadyCompleted_throws() {
        pendingReservation.setStatus(Reservation.Status.COMPLETED);
        when(reservationRepository.findByIdWithDetails(reservationId))
                .thenReturn(Optional.of(pendingReservation));

        assertThatThrownBy(() ->
            reservationService.cancel(reservationId, null, customerId, "CUSTOMER"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("terminal state");
    }

    @Test
    void cancel_byOtherCustomer_throwsAccessDenied() {
        UUID otherId = UUID.randomUUID();
        when(reservationRepository.findByIdWithDetails(reservationId))
                .thenReturn(Optional.of(pendingReservation));
        when(pharmacyUserRepository.existsByPharmacyIdAndUserId(any(), eq(otherId)))
                .thenReturn(false);

        assertThatThrownBy(() ->
            reservationService.cancel(reservationId, null, otherId, "CUSTOMER"))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── complete ──────────────────────────────────────────────────

    @Test
    void complete_fromConfirmed_succeeds() {
        pendingReservation.setStatus(Reservation.Status.CONFIRMED);
        when(reservationRepository.findByIdWithDetails(reservationId))
                .thenReturn(Optional.of(pendingReservation));
        when(pharmacyUserRepository.existsByPharmacyIdAndUserId(pharmacyId, customerId))
                .thenReturn(true);
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReservationResponse resp = reservationService.complete(
            reservationId, customerId, "PHARMACY_STAFF");

        assertThat(resp.status()).isEqualTo(Reservation.Status.COMPLETED);
    }

    @Test
    void complete_fromPending_throws() {
        when(reservationRepository.findByIdWithDetails(reservationId))
                .thenReturn(Optional.of(pendingReservation));
        when(pharmacyUserRepository.existsByPharmacyIdAndUserId(any(), any())).thenReturn(true);

        assertThatThrownBy(() ->
            reservationService.complete(reservationId, customerId, "PHARMACY_STAFF"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("READY, CONFIRMED, or PAID");
    }

    // ── Status helpers ────────────────────────────────────────────

    @Test
    void statusIsTerminalForCorrectValues() {
        assertThat(Reservation.Status.COMPLETED.isTerminal()).isTrue();
        assertThat(Reservation.Status.CANCELLED.isTerminal()).isTrue();
        assertThat(Reservation.Status.EXPIRED.isTerminal()).isTrue();
        assertThat(Reservation.Status.PENDING.isTerminal()).isFalse();
        assertThat(Reservation.Status.CONFIRMED.isTerminal()).isFalse();
    }

    @Test
    void stockDecrementedFlagCorrect() {
        assertThat(Reservation.Status.CONFIRMED.isStockDecremented()).isTrue();
        assertThat(Reservation.Status.PAID.isStockDecremented()).isTrue();
        assertThat(Reservation.Status.READY.isStockDecremented()).isTrue();
        assertThat(Reservation.Status.COMPLETED.isStockDecremented()).isTrue();
        assertThat(Reservation.Status.PENDING.isStockDecremented()).isFalse();
        assertThat(Reservation.Status.CANCELLED.isStockDecremented()).isFalse();
    }
}
