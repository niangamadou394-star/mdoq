package com.medoq.backend.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Commission report for a date range.
 * Contains a summary row and a per-pharmacy breakdown.
 */
public record CommissionReportDto(
        Instant        from,
        Instant        to,
        BigDecimal     totalGross,
        BigDecimal     totalCommission,
        BigDecimal     totalNet,
        long           totalTransactions,
        List<PharmacyRow> rows
) {
    public record PharmacyRow(
            UUID       pharmacyId,
            String     pharmacyName,
            long       transactionCount,
            BigDecimal grossRevenue,
            BigDecimal commissionAmount,
            BigDecimal netAmount
    ) {}
}
