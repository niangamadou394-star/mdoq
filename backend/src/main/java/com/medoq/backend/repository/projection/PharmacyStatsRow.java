package com.medoq.backend.repository.projection;

import java.math.BigDecimal;

/**
 * Projection for per-pharmacy reservation + revenue stats.
 */
public interface PharmacyStatsRow {
    Long       getTotalReservations();
    Long       getCompletedReservations();
    Long       getCancelledReservations();
    Long       getExpiredReservations();
    BigDecimal getTotalRevenue();
    BigDecimal getTotalCommission();
    BigDecimal getNetRevenue();
}
