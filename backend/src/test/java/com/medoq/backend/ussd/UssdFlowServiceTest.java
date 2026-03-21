package com.medoq.backend.ussd;

import com.medoq.backend.dto.search.MedicationSearchResultDto;
import com.medoq.backend.dto.search.PharmacyStockDto;
import com.medoq.backend.entity.Reservation;
import com.medoq.backend.entity.User;
import com.medoq.backend.repository.ReservationRepository;
import com.medoq.backend.repository.UserRepository;
import com.medoq.backend.service.MedicationSearchService;
import com.medoq.backend.service.PaymentService;
import com.medoq.backend.service.ReservationService;
import com.medoq.backend.service.ussd.UssdFlowService;
import com.medoq.backend.service.ussd.UssdSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UssdFlowServiceTest {

    @Mock UssdSessionService       sessionService;
    @Mock MedicationSearchService  searchService;
    @Mock ReservationService       reservationService;
    @Mock ReservationRepository    reservationRepository;
    @Mock UserRepository           userRepository;
    @Mock PaymentService           paymentService;

    @InjectMocks
    UssdFlowService ussdFlowService;

    private static final String SESSION = "AT_SESSION_001";
    private static final String PHONE   = "+221771234567";

    // ── Main menu ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Empty text → main menu CON")
    void mainMenu_onEmptyText() {
        String response = ussdFlowService.handle(SESSION, PHONE, "");

        assertThat(response).startsWith("CON ");
        assertThat(response).contains("1.");   // search option
        assertThat(response).contains("2.");   // my reservations
        assertThat(response).contains("3.");   // cancel
    }

    @Test
    @DisplayName("Null text treated as empty → main menu")
    void mainMenu_onNullText() {
        String response = ussdFlowService.handle(SESSION, PHONE, null);

        assertThat(response).startsWith("CON ");
    }

    // ── Option 1: search flow ─────────────────────────────────────────────────

    @Test
    @DisplayName("text=1 → prompt for medication name")
    void search_promptName() {
        String response = ussdFlowService.handle(SESSION, PHONE, "1");

        assertThat(response).startsWith("CON ");
        // Should ask user to enter medication name
        assertThat(response.toLowerCase()).containsAnyOf("médicament", "nom", "recherche");
    }

    @Test
    @DisplayName("text=1*paracetamol → list pharmacies with stock")
    void search_listResults() {
        PharmacyStockDto stock = pharmacyStock("Pharmacie du Plateau", 10, new BigDecimal("500"));
        MedicationSearchResultDto result = medicationResult("Paracétamol 500mg",
            List.of(stock));

        when(searchService.search(any())).thenReturn(List.of(result));

        String response = ussdFlowService.handle(SESSION, PHONE, "1*paracetamol");

        assertThat(response).startsWith("CON ");
        assertThat(response).contains("Paracétamol");

        // Should cache results in session
        verify(sessionService).store(eq(SESSION), anyString(), any());
    }

    @Test
    @DisplayName("text=1*paracetamol when no results → END with message")
    void search_noResults() {
        when(searchService.search(any())).thenReturn(List.of());

        String response = ussdFlowService.handle(SESSION, PHONE, "1*unknown_drug_xyz");

        assertThat(response).startsWith("END ");
    }

    // ── Option 2: my reservations ─────────────────────────────────────────────

    @Test
    @DisplayName("text=2 when user not found → END with message")
    void myReservations_userNotFound() {
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.empty());

        String response = ussdFlowService.handle(SESSION, PHONE, "2");

        assertThat(response).startsWith("END ");
    }

    @Test
    @DisplayName("text=2 with no active reservations → END with message")
    void myReservations_empty() {
        User user = User.builder().id(UUID.randomUUID()).phone(PHONE).build();
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(user));
        when(reservationRepository.findByCustomerAndStatusIn(any(), any()))
            .thenReturn(List.of());

        String response = ussdFlowService.handle(SESSION, PHONE, "2");

        assertThat(response).startsWith("END ");
    }

    // ── Option 3: cancel flow ─────────────────────────────────────────────────

    @Test
    @DisplayName("text=3 → prompt for reference")
    void cancel_promptReference() {
        String response = ussdFlowService.handle(SESSION, PHONE, "3");

        assertThat(response).startsWith("CON ");
        assertThat(response.toLowerCase()).containsAnyOf("référence", "ref", "numéro");
    }

    @Test
    @DisplayName("text=3*REF when reservation not found → END with message")
    void cancel_refNotFound() {
        when(reservationRepository.findByReference("MQ-260101-00001"))
            .thenReturn(Optional.empty());

        String response = ussdFlowService.handle(SESSION, PHONE, "3*MQ-260101-00001");

        assertThat(response).startsWith("END ");
    }

    // ── Invalid option ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Unknown option at depth 1 → END with invalid message")
    void unknownOption_endsSession() {
        String response = ussdFlowService.handle(SESSION, PHONE, "9");

        assertThat(response).startsWith("END ");
    }

    @Test
    @DisplayName("Very deep text → END gracefully")
    void deepText_handled() {
        String response = ussdFlowService.handle(SESSION, PHONE, "1*2*3*4*5*6*7*8");

        assertThat(response).startsWith("END ");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PharmacyStockDto pharmacyStock(String name, int qty, BigDecimal price) {
        PharmacyStockDto dto = new PharmacyStockDto();
        dto.setPharmacyId(UUID.randomUUID());
        dto.setPharmacyName(name);
        dto.setQuantity(qty);
        dto.setUnitPrice(price);
        dto.setDistanceKm(1.2);
        return dto;
    }

    private MedicationSearchResultDto medicationResult(String name, List<PharmacyStockDto> stocks) {
        MedicationSearchResultDto dto = new MedicationSearchResultDto();
        dto.setMedicationId(UUID.randomUUID());
        dto.setName(name);
        dto.setPharmacies(stocks);
        return dto;
    }
}
