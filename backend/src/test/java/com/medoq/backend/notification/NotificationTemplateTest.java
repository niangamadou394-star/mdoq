package com.medoq.backend.notification;

import com.medoq.backend.entity.*;
import com.medoq.backend.entity.notification.NotificationTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTemplateTest {

    private Reservation reservation;

    @BeforeEach
    void setUp() {
        User customer = User.builder()
            .id(UUID.randomUUID())
            .phone("+221771234567")
            .firstName("Amadou")
            .lastName("Niang")
            .build();

        Pharmacy pharmacy = new Pharmacy();
        pharmacy.setId(UUID.randomUUID());
        pharmacy.setName("Pharmacie du Plateau");

        Medication med = new Medication();
        med.setId(UUID.randomUUID());
        med.setName("Paracétamol 500mg");

        ReservationItem item = new ReservationItem();
        item.setMedication(med);
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("500.00"));

        reservation = new Reservation();
        reservation.setId(UUID.randomUUID());
        reservation.setReference("MQ-260321-01000");
        reservation.setCustomer(customer);
        reservation.setPharmacy(pharmacy);
        reservation.setExpiresAt(Instant.now().plus(2, ChronoUnit.HOURS));
        reservation.setItems(new ArrayList<>(List.of(item)));
        reservation.setStatus(Reservation.Status.CONFIRMED);
    }

    // ── Title tests ───────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} title is not blank")
    @EnumSource(NotificationTemplate.class)
    @DisplayName("Every template has a non-blank title")
    void allTemplates_haveNonBlankTitle(NotificationTemplate tpl) {
        assertThat(tpl.title()).isNotBlank();
    }

    // ── Body tests ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RESERVATION_CONFIRMED body contains ref and pharmacy name")
    void reservationConfirmed_bodyContainsKeyInfo() {
        String body = NotificationTemplate.RESERVATION_CONFIRMED.body(reservation);

        assertThat(body).contains("MQ-260321-01000");
        assertThat(body).contains("Pharmacie du Plateau");
        assertThat(body).contains("Paracétamol");
    }

    @Test
    @DisplayName("RESERVATION_EXPIRING body mentions 30 min")
    void reservationExpiring_bodyMentionsTime() {
        String body = NotificationTemplate.RESERVATION_EXPIRING.body(reservation);

        assertThat(body).contains("MQ-260321-01000");
        assertThat(body).containsAnyOf("30 min", "30min");
    }

    @Test
    @DisplayName("RESERVATION_READY body contains pharmacy name")
    void reservationReady_bodyContainsPharmacy() {
        String body = NotificationTemplate.RESERVATION_READY.body(reservation);

        assertThat(body).contains("Pharmacie du Plateau");
        assertThat(body).contains("MQ-260321-01000");
    }

    @Test
    @DisplayName("PAYMENT_CONFIRMED body contains ref")
    void paymentConfirmed_bodyContainsRef() {
        String body = NotificationTemplate.PAYMENT_CONFIRMED.body(reservation);

        assertThat(body).contains("MQ-260321-01000");
    }

    // ── SMS body length constraint ────────────────────────────────────────────

    @ParameterizedTest(name = "{0} smsBody fits in 160 chars")
    @EnumSource(value = NotificationTemplate.class, names = {"STOCK_ALERT"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("smsBody is at most 160 characters")
    void smsBody_fitsInOneSmsSegment(NotificationTemplate tpl) {
        String sms = tpl.smsBody(reservation);
        assertThat(sms.length()).isLessThanOrEqualTo(160);
    }

    // ── Multi-item primary medication name ────────────────────────────────────

    @Test
    @DisplayName("RESERVATION_CONFIRMED with 3 items shows +2 other(s)")
    void reservationConfirmed_multipleItems() {
        Medication med2 = new Medication();
        med2.setName("Amoxicilline 500mg");
        ReservationItem item2 = new ReservationItem();
        item2.setMedication(med2);
        item2.setQuantity(1);
        item2.setUnitPrice(BigDecimal.valueOf(1200));

        Medication med3 = new Medication();
        med3.setName("Ibuprofène 400mg");
        ReservationItem item3 = new ReservationItem();
        item3.setMedication(med3);
        item3.setQuantity(1);
        item3.setUnitPrice(BigDecimal.valueOf(600));

        reservation.getItems().add(item2);
        reservation.getItems().add(item3);

        String body = NotificationTemplate.RESERVATION_CONFIRMED.body(reservation);

        assertThat(body).contains("Paracétamol");
        assertThat(body).contains("+2");
    }
}
