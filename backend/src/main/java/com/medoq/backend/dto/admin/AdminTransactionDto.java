package com.medoq.backend.dto.admin;

import com.medoq.backend.entity.Payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminTransactionDto(
        UUID           id,
        String         transactionRef,
        String         reservationRef,
        String         customerPhone,
        String         pharmacyName,
        BigDecimal     amount,
        BigDecimal     commissionAmount,
        BigDecimal     netAmount,
        Payment.Method method,
        Payment.Status status,
        Instant        paidAt,
        Instant        createdAt
) {
    public static AdminTransactionDto from(Payment p) {
        var r = p.getReservation();
        return new AdminTransactionDto(
            p.getId(),
            p.getTransactionRef(),
            r != null ? r.getReference() : null,
            r != null && r.getCustomer() != null ? r.getCustomer().getPhone() : null,
            r != null && r.getPharmacy()  != null ? r.getPharmacy().getName()  : null,
            p.getAmount(),
            p.getCommissionAmount(),
            p.getNetAmount(),
            p.getMethod(),
            p.getStatus(),
            p.getPaidAt(),
            p.getCreatedAt()
        );
    }
}
