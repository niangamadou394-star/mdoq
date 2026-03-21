package com.medoq.backend.dto.payment;

import com.medoq.backend.entity.Payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
    UUID           id,
    UUID           reservationId,
    String         reservationReference,
    Payment.Method method,
    Payment.Status status,
    BigDecimal     amount,
    BigDecimal     commissionRate,
    BigDecimal     commissionAmount,
    BigDecimal     netAmount,
    String         transactionRef,
    Instant        paidAt,
    Instant        createdAt,

    /** Redirect URL for Wave checkout (null for Orange Money). */
    String         checkoutUrl,

    /** USSD push status message for Orange Money. */
    String         ussdMessage
) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
            p.getId(),
            p.getReservation().getId(),
            p.getReservation().getReference(),
            p.getMethod(),
            p.getStatus(),
            p.getAmount(),
            p.getCommissionRate(),
            p.getCommissionAmount(),
            p.getNetAmount(),
            p.getTransactionRef(),
            p.getPaidAt(),
            p.getCreatedAt(),
            null, null   // checkoutUrl / ussdMessage set by caller
        );
    }

    public PaymentResponse withCheckoutUrl(String url) {
        return new PaymentResponse(id, reservationId, reservationReference, method, status,
            amount, commissionRate, commissionAmount, netAmount,
            transactionRef, paidAt, createdAt, url, null);
    }

    public PaymentResponse withUssdMessage(String msg) {
        return new PaymentResponse(id, reservationId, reservationReference, method, status,
            amount, commissionRate, commissionAmount, netAmount,
            transactionRef, paidAt, createdAt, null, msg);
    }
}
