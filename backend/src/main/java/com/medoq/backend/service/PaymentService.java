package com.medoq.backend.service;

import com.medoq.backend.dto.payment.*;
import com.medoq.backend.entity.*;
import com.medoq.backend.exception.BusinessException;
import com.medoq.backend.exception.ResourceNotFoundException;
import com.medoq.backend.repository.*;
import com.medoq.backend.service.orange.OrangeApiClient;
import com.medoq.backend.service.wave.WaveApiClient;
import com.medoq.backend.util.AesEncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository      paymentRepository;
    private final ReservationRepository  reservationRepository;
    private final UserRepository         userRepository;
    private final CommissionService      commissionService;
    private final WaveApiClient          waveApiClient;
    private final OrangeApiClient        orangeApiClient;
    private final InvoiceService         invoiceService;
    private final EmailService           emailService;
    private final AuditLogService        auditLogService;
    private final NotificationService    notificationService;
    private final AesEncryptionUtil      aesUtil;

    // ── Wave: initiate ────────────────────────────────────────────

    @Transactional
    public PaymentResponse initiateWave(UUID reservationId, UUID customerId) {
        Reservation reservation = loadActiveReservation(reservationId, customerId);
        User customer           = reservation.getCustomer();

        Payment payment = buildPendingPayment(reservation, customer, Payment.Method.WAVE);
        payment = paymentRepository.save(payment);

        // Call Wave API
        WaveApiClient.CheckoutSession session;
        try {
            session = waveApiClient.createCheckoutSession(
                payment.getAmount(), payment.getId().toString());
        } catch (Exception e) {
            payment.setStatus(Payment.Status.FAILED);
            payment.setFailedAt(Instant.now());
            payment.setFailureReason(e.getMessage());
            paymentRepository.save(payment);
            auditLogService.logFailure(AuditLogService.ACTION_PAYMENT_INITIATED,
                "Payment", payment.getId(), customer, null,
                Map.of("method", "WAVE", "reservationId", reservationId.toString()),
                e.getMessage());
            throw e;
        }

        // Store encrypted session ID
        payment.setProviderSession(aesUtil.encrypt(session.id()));
        payment.setMetadata(Map.of(
            "waveSessionId",  session.id(),
            "checkoutStatus", session.checkoutStatus()
        ));
        payment = paymentRepository.save(payment);

        auditLogService.logSuccess(AuditLogService.ACTION_PAYMENT_INITIATED,
            "Payment", payment.getId(), customer, null,
            Map.of("method", "WAVE", "amount", payment.getAmount().toString()));

        log.info("Wave checkout initiated: payment={} session={}", payment.getId(), session.id());
        return PaymentResponse.from(payment).withCheckoutUrl(session.waveLaunchUrl());
    }

    // ── Orange Money: initiate ────────────────────────────────────

    @Transactional
    public PaymentResponse initiateOrange(UUID reservationId, UUID customerId, String customerPhone) {
        Reservation reservation = loadActiveReservation(reservationId, customerId);
        User customer           = reservation.getCustomer();

        String phone = customerPhone != null ? customerPhone : customer.getPhone();
        Payment payment = buildPendingPayment(reservation, customer, Payment.Method.ORANGE_MONEY);
        payment = paymentRepository.save(payment);

        OrangeApiClient.InitiateResponse response;
        try {
            response = orangeApiClient.initiatePayment(
                payment.getAmount(),
                payment.getId().toString(),
                reservation.getReference()
            );
        } catch (Exception e) {
            payment.setStatus(Payment.Status.FAILED);
            payment.setFailedAt(Instant.now());
            payment.setFailureReason(e.getMessage());
            paymentRepository.save(payment);
            auditLogService.logFailure(AuditLogService.ACTION_PAYMENT_INITIATED,
                "Payment", payment.getId(), customer, null,
                Map.of("method", "ORANGE_MONEY", "reservationId", reservationId.toString()),
                e.getMessage());
            throw e;
        }

        if (response != null && response.data() != null) {
            payment.setProviderSession(aesUtil.encrypt(response.data().paymentToken()));
            payment.setMetadata(Map.of(
                "paymentToken", response.data().paymentToken(),
                "paymentUrl",   response.data().paymentUrl()  != null ? response.data().paymentUrl() : ""
            ));
        }
        payment = paymentRepository.save(payment);

        auditLogService.logSuccess(AuditLogService.ACTION_PAYMENT_INITIATED,
            "Payment", payment.getId(), customer, null,
            Map.of("method", "ORANGE_MONEY", "phone", phone));

        String ussdMsg = "Un paiement Orange Money de " + payment.getAmount().toPlainString()
            + " FCFA a été initié. Réf: #" + reservation.getReference()
            + ". Veuillez confirmer sur votre téléphone " + phone + ".";

        log.info("Orange Money payment initiated: payment={}", payment.getId());
        return PaymentResponse.from(payment).withUssdMessage(ussdMsg);
    }

    // ── Wave webhook ──────────────────────────────────────────────

    @Transactional
    public void processWaveWebhook(WaveWebhookEvent event, String rawPayload, String ipAddress) {
        // Idempotency: skip if we already processed this event
        if (paymentRepository.existsByTransactionRef(event.id())) {
            log.warn("Duplicate Wave webhook event: {}", event.id());
            return;
        }

        auditLogService.logSuccess(AuditLogService.ACTION_WEBHOOK_RECEIVED,
            "WebhookEvent", null, null, ipAddress,
            Map.of("provider", "WAVE", "eventId", event.id(),
                   "eventType", event.type() != null ? event.type() : "unknown"));

        if (event.data() == null || event.data().clientReference() == null) {
            log.warn("Wave webhook missing client_reference: {}", event.id());
            return;
        }

        UUID paymentId;
        try {
            paymentId = UUID.fromString(event.data().clientReference());
        } catch (IllegalArgumentException e) {
            log.warn("Wave webhook invalid client_reference: {}", event.data().clientReference());
            return;
        }

        Payment payment = paymentRepository.findByIdWithDetails(paymentId)
                .orElse(null);
        if (payment == null) {
            log.warn("Wave webhook: payment not found for id {}", paymentId);
            return;
        }

        if (event.isPaymentSucceeded()) {
            finalizePayment(payment, event.id(), ipAddress);
        } else if (event.isPaymentFailed()) {
            failPayment(payment, event.id(),
                event.data().lastPaymentError() != null
                    ? event.data().lastPaymentError()
                    : "Wave payment failed",
                ipAddress);
        }
    }

    // ── Orange Money callback ─────────────────────────────────────

    @Transactional
    public void processOrangeCallback(OrangeCallbackPayload payload, String ipAddress) {
        // Verify notif token
        if (!orangeApiClient.verifyCallbackToken(payload.notifToken())) {
            log.warn("Invalid Orange Money notif_token from {}", ipAddress);
            throw new BusinessException("Invalid Orange Money callback token.");
        }

        // Idempotency
        if (payload.txnid() != null && paymentRepository.existsByTransactionRef(payload.txnid())) {
            log.warn("Duplicate Orange Money callback: txnid={}", payload.txnid());
            return;
        }

        auditLogService.logSuccess(AuditLogService.ACTION_WEBHOOK_RECEIVED,
            "WebhookEvent", null, null, ipAddress,
            Map.of("provider", "ORANGE_MONEY", "txnid", payload.txnid() != null ? payload.txnid() : ""));

        UUID paymentId;
        try {
            paymentId = UUID.fromString(payload.orderId());
        } catch (IllegalArgumentException e) {
            log.warn("Orange callback invalid order_id: {}", payload.orderId());
            return;
        }

        Payment payment = paymentRepository.findByIdWithDetails(paymentId).orElse(null);
        if (payment == null) {
            log.warn("Orange callback: payment not found for id {}", paymentId);
            return;
        }

        if (payload.isSuccess()) {
            finalizePayment(payment, payload.txnid(), ipAddress);
        } else if (payload.isFailed()) {
            failPayment(payment, payload.txnid(), payload.message(), ipAddress);
        }
    }

    // ── Read ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PaymentResponse getById(UUID paymentId) {
        Payment payment = paymentRepository.findByIdWithDetails(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getByReservation(UUID reservationId) {
        return paymentRepository.findByReservationIdOrderByCreatedAtDesc(reservationId)
                .stream().map(PaymentResponse::from).toList();
    }

    // ── Internal helpers ──────────────────────────────────────────

    private Reservation loadActiveReservation(UUID reservationId, UUID customerId) {
        Reservation r = reservationRepository.findByIdWithDetails(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", reservationId));

        if (!r.getCustomer().getId().equals(customerId)) {
            throw new BusinessException("Reservation does not belong to this customer.");
        }
        if (r.getStatus().isTerminal()) {
            throw new BusinessException("Cannot initiate payment for a " + r.getStatus() + " reservation.");
        }
        if (r.getStatus() == Reservation.Status.COMPLETED) {
            throw new BusinessException("Reservation is already completed.");
        }
        // Check for existing pending payment to avoid duplicates
        List<Payment> existing = paymentRepository.findByReservationIdOrderByCreatedAtDesc(reservationId);
        if (existing.stream().anyMatch(p -> p.getStatus() == Payment.Status.PENDING)) {
            throw new BusinessException("A payment is already pending for this reservation.");
        }
        return r;
    }

    private Payment buildPendingPayment(Reservation reservation, User customer, Payment.Method method) {
        BigDecimal amount     = reservation.getTotalAmount();
        BigDecimal rate       = commissionService.currentRate();
        BigDecimal commission = commissionService.compute(amount);
        BigDecimal net        = amount.subtract(commission);

        return Payment.builder()
                .reservation(reservation)
                .customer(customer)
                .amount(amount)
                .method(method)
                .status(Payment.Status.PENDING)
                .commissionRate(rate)
                .commissionAmount(commission)
                .netAmount(net)
                .build();
    }

    private void finalizePayment(Payment payment, String txnRef, String ipAddress) {
        payment.setStatus(Payment.Status.COMPLETED);
        payment.setTransactionRef(txnRef);
        payment.setProviderRef(txnRef);
        payment.setPaidAt(Instant.now());
        paymentRepository.save(payment);

        // Advance reservation to PAID
        Reservation reservation = payment.getReservation();
        if (!reservation.getStatus().isTerminal()
                && reservation.getStatus() != Reservation.Status.COMPLETED) {
            reservation.setStatus(Reservation.Status.PAID);
            reservationRepository.save(reservation);
        }

        // Audit
        auditLogService.logStateChange(
            AuditLogService.ACTION_PAYMENT_COMPLETED,
            "Payment", payment.getId(), payment.getCustomer(), ipAddress,
            Map.of("status", "PENDING"),
            Map.of("status", "COMPLETED", "txnRef", txnRef,
                   "amount", payment.getAmount().toString()));

        log.info("Payment completed: {} txnRef={}", payment.getId(), txnRef);

        // Generate + send invoice asynchronously
        sendInvoiceAsync(payment);
    }

    private void failPayment(Payment payment, String txnRef, String reason, String ipAddress) {
        payment.setStatus(Payment.Status.FAILED);
        payment.setTransactionRef(txnRef);
        payment.setFailedAt(Instant.now());
        payment.setFailureReason(reason);
        paymentRepository.save(payment);

        auditLogService.logFailure(
            AuditLogService.ACTION_PAYMENT_FAILED,
            "Payment", payment.getId(), payment.getCustomer(), ipAddress,
            Map.of("txnRef", txnRef != null ? txnRef : ""),
            reason);

        log.warn("Payment failed: {} reason={}", payment.getId(), reason);
    }

    private void sendInvoiceAsync(Payment payment) {
        try {
            byte[] pdf      = invoiceService.generateInvoice(payment);
            String filename = invoiceService.invoiceFilename(payment);
            String email    = payment.getCustomer().getEmail();

            if (email != null && !email.isBlank()) {
                String subject = "Votre facture Medoq — " + payment.getReservation().getReference();
                String body    = buildInvoiceEmailHtml(payment);
                emailService.send(email, subject, body, filename, pdf);

                auditLogService.logSuccess(AuditLogService.ACTION_INVOICE_SENT,
                    "Payment", payment.getId(), payment.getCustomer(), null,
                    Map.of("email", email, "filename", filename));
            }
        } catch (Exception e) {
            log.error("Failed to generate/send invoice for payment {}: {}",
                payment.getId(), e.getMessage());
        }
    }

    private String buildInvoiceEmailHtml(Payment payment) {
        return String.format("""
            <html><body style="font-family:Arial,sans-serif;color:#111827">
            <h2 style="color:#1A56DB">Merci pour votre commande Medoq!</h2>
            <p>Bonjour %s,</p>
            <p>Votre paiement de <strong>%s FCFA</strong> a bien été reçu.</p>
            <p>Réservation: <strong>#%s</strong> chez <strong>%s</strong></p>
            <p>Veuillez trouver votre facture en pièce jointe.</p>
            <br><p style="color:#6B7280;font-size:12px">
            L'équipe Medoq — noreply@medoq.sn</p>
            </body></html>
            """,
            payment.getCustomer().getFirstName(),
            payment.getAmount().toPlainString(),
            payment.getReservation().getReference(),
            payment.getReservation().getPharmacy().getName()
        );
    }
}
