package com.medoq.backend.dto.admin;

import java.math.BigDecimal;

public record DashboardStatsDto(
        // Pharmacies
        long       totalPharmacies,
        long       activePharmacies,
        long       pendingApproval,
        long       suspendedPharmacies,

        // Users
        long       totalUsers,
        long       totalCustomers,
        long       blockedUsers,

        // Reservations
        long       reservationsToday,
        long       reservationsThisMonth,

        // Revenue (COMPLETED payments only)
        BigDecimal revenueToday,
        BigDecimal revenueThisMonth,
        BigDecimal commissionToday,
        BigDecimal commissionThisMonth
) {}
