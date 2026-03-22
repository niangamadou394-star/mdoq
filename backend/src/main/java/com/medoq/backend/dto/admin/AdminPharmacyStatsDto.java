package com.medoq.backend.dto.admin;

import java.math.BigDecimal;
import java.util.UUID;

public record AdminPharmacyStatsDto(
        UUID       pharmacyId,
        String     pharmacyName,
        long       totalReservations,
        long       completedReservations,
        long       cancelledReservations,
        long       expiredReservations,
        BigDecimal totalRevenue,
        BigDecimal totalCommission,
        BigDecimal netRevenue,
        BigDecimal rating,
        int        reviewCount
) {}
