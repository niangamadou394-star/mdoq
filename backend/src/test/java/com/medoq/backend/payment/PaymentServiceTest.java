package com.medoq.backend.payment;

import com.medoq.backend.dto.payment.PaymentResponse;
import com.medoq.backend.dto.payment.WaveWebhookEvent;
import com.medoq.backend.entity.*;
import com.medoq.backend.exception.BusinessException;
import com.medoq.backend.repository.*;
import com.medoq.backend.service.*;
import com.medoq.backend.service.orange.OrangeApiClient;
import com.medoq.backend.service.wave.WaveApiClient;
import com.medoq.backend.util.AesEncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentRepository     paymentRepository;
    @Mock ReservationRepository reservationRepository;
    @Mock UserRepository        userRepository;
    @Mock CommissionService     commissionService;
    @Mock WaveApiClient         waveApiClient;
    @Mock OrangeApiClient       orangeApiClient;
    @Mock InvoiceService        invoiceService;
    @Mock EmailService          emailService;
    @Mock AuditLogService       auditLogService;
    @Mock NotificationService   notificationService;
    @Mock AesEncryptionUtil     aesUtil;

    @InjectMocks PaymentService paymentService;

    private UUID customerId, reservationId, paymentId;
    private User customer;
    private Pharmacy pharmacy;
    private Reservation reservation;

    @BeforeEach
    void setUp() {
        customerId    = UUID.randomUUID();
        reservationId = UUID.randomUUID();
        paymentId     = UUID.randomUUID();

        customer = User.builder()
                .id(customerId).phone("+221771234567").firstName("Amadou").lastName("Diallo")
                .email("amadou@example.com")
                .role(User.Role.CUSTOMER).status(User.Status.ACTIVE)
                .build();

        pharmacy = Pharmacy.builder()
                .id(UUID.randomUUID()).name("Pharmacie du Plateau")
                .address("Rue de Thiong").city("Dakar").phone("+221338210001")
                .status(Pharmacy.Status.ACTIVE).owner(customer).build();

        reservation = Reservation.builder()
                .id(reservationId).reference("MQ-240321-01000")
                .customer(customer).pharmacy(pharmacy)
                .status(Reservation.Status.CONFIRMED)
                .totalAmount(BigDecimal.valueOf(3500))
                .expiresAt(Instant.now().plus(2, ChronoUnit.HOURS))
                .items(new ArrayList<>()).build();
    }

    // ── Wave initiation ───────────────────────────────────────────

    @Test
    void initiateWave_success_returnsCheckoutUrl() {
        when(reservationRepository.findByIdWithDetails(reservationId))
                .thenReturn(Optional.of(reservation));
        when(paymentRepository.findByReservationIdOrderByCreatedAtDesc(reservationId))
                .thenReturn(List.of());
        when(commissionService.currentRate()).thenReturn(new BigDecimal("0.0150"));
        when(commissionService.compute(any())).thenReturn(new BigDecimal("52.50"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(paymentId);
            return p;
        });
        when(waveApiClient.createCheckoutSession(any(), any()))
                .thenReturn(new WaveApiClient.CheckoutSession(
                    "sess_123", "https://pay.wave.com/checkout/sess_123",
                    "open", paymentId.toString(),
                    BigDecimal.valueOf(3500), "XOF", null));
        when(aesUtil.encrypt(any())).thenReturn("encrypted_session");

        PaymentResponse resp = paymentService.initiateWave(reservationId, customerId);

        assertThat(resp.checkoutUrl()).contains("wave.com");
        assertThat(resp.status()).isEqualTo(Payment.Status.PENDING);
        assertThat(resp.amount()).isEqualByComparingTo("3500");
    }

    @Test
    void initiateWave_alreadyTerminal_throws() {
        reservation.setStatus(Reservation.Status.CANCELLED);
        when(reservationRepository.findByIdWithDetails(reservationId))
                .thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> paymentService.initiateWave(reservationId, customerId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CANCELLED");
    }

    @Test
    void initiateWave_duplicatePending_throws() {
        when(reservationRepository.findByIdWithDetails(reservationId))
                .thenReturn(Optional.of(reservation));
        Payment existing = Payment.builder()
                .id(UUID.randomUUID()).status(Payment.Status.PENDING).build();
        when(paymentRepository.findByReservationIdOrderByCreatedAtDesc(reservationId))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> paymentService.initiateWave(reservationId, customerId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already pending");
    }

    // ── Wave webhook ──────────────────────────────────────────────

    @Test
    void processWaveWebhook_success_completesPayment() {
        Payment payment = Payment.builder()
                .id(paymentId).reservation(reservation).customer(customer)
                .amount(BigDecimal.valueOf(3500)).method(Payment.Method.WAVE)
                .status(Payment.Status.PENDING)
                .commissionRate(new BigDecimal("0.0150"))
                .commissionAmount(new BigDecimal("52.50"))
                .netAmount(new BigDecimal("3447.50")).build();

        WaveWebhookEvent event = new WaveWebhookEvent(
            "evt_abc", "checkout.session.completed",
            new WaveWebhookEvent.Data(
                "sess_123", "complete", "succeeded",
                paymentId.toString(), BigDecimal.valueOf(3500), "XOF",
                Instant.now().toString(), null),
            Instant.now().toString());

        when(paymentRepository.existsByTransactionRef("evt_abc")).thenReturn(false);
        when(paymentRepository.findByIdWithDetails(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceService.generateInvoice(any())).thenReturn(new byte[]{1, 2, 3});
        when(invoiceService.invoiceFilename(any())).thenReturn("Facture_MQ-240321-01000.pdf");

        paymentService.processWaveWebhook(event, "{}", "1.2.3.4");

        assertThat(payment.getStatus()).isEqualTo(Payment.Status.COMPLETED);
        assertThat(payment.getTransactionRef()).isEqualTo("evt_abc");
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(reservation.getStatus()).isEqualTo(Reservation.Status.PAID);
    }

    @Test
    void processWaveWebhook_duplicate_skipped() {
        WaveWebhookEvent event = new WaveWebhookEvent(
            "evt_dup", "checkout.session.completed",
            new WaveWebhookEvent.Data("sess_123", "complete", "succeeded",
                paymentId.toString(), BigDecimal.valueOf(3500), "XOF", null, null),
            null);

        when(paymentRepository.existsByTransactionRef("evt_dup")).thenReturn(true);

        paymentService.processWaveWebhook(event, "{}", "1.2.3.4");

        verify(paymentRepository, never()).findByIdWithDetails(any());
    }

    // ── HMAC signature utility ────────────────────────────────────

    @Test
    void hmacVerify_validSignature_returns_true() {
        String secret  = "my_webhook_secret";
        String payload = """{"id":"evt_1","type":"checkout.session.completed"}""";
        String sig     = com.medoq.backend.util.HmacSignatureUtil.computeHmac256(payload, secret);

        assertThat(com.medoq.backend.util.HmacSignatureUtil.verify(payload, secret, sig))
                .isTrue();
    }

    @Test
    void hmacVerify_wrongSignature_returns_false() {
        assertThat(com.medoq.backend.util.HmacSignatureUtil.verify(
            "payload", "secret", "sha256=wrongsignature"))
                .isFalse();
    }
}
