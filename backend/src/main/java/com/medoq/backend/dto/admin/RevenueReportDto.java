package com.medoq.backend.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Revenue report per pharmacy for a given period.
 */
public record RevenueReportDto(
        Instant        from,
        Instant        to,
        BigDecimal     totalRevenue,
        BigDecimal     totalCommission,
        long           totalTransactions,
        List<PharmacyRow> byPharmacy
) {
    public record PharmacyRow(
            UUID       pharmacyId,
            String     pharmacyName,
            long       transactionCount,
            BigDecimal revenue,
            BigDecimal commission,
            BigDecimal netRevenue
    ) {}
}
