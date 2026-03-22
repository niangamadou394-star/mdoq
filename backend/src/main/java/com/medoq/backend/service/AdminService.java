package com.medoq.backend.service;

import com.medoq.backend.dto.admin.*;
import com.medoq.backend.entity.Pharmacy;
import com.medoq.backend.entity.User;
import com.medoq.backend.entity.Payment;
import com.medoq.backend.entity.AuditLog;
import com.medoq.backend.exception.ResourceNotFoundException;
import com.medoq.backend.repository.*;
import com.medoq.backend.repository.projection.CommissionRow;
import com.medoq.backend.repository.projection.PharmacyStatsRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final PharmacyRepository    pharmacyRepo;
    private final UserRepository        userRepo;
    private final PaymentRepository     paymentRepo;
    private final ReservationRepository reservationRepo;
    private final AuditLogRepository    auditLogRepo;
    private final AuditLogService       auditLogService;

    // ── PHARMACIES ────────────────────────────────────────────────

    public PageResponse<AdminPharmacyDto> listPharmacies(
            Pharmacy.Status status, String city, String search, Pageable pageable) {
        Page<Pharmacy> page = pharmacyRepo.findAdminList(status, city, search, pageable);
        return PageResponse.of(page.map(AdminPharmacyDto::from));
    }

    @Transactional
    public AdminPharmacyDto activatePharmacy(UUID id, String adminUserId) {
        Pharmacy p = pharmacyRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Pharmacy", id));
        pharmacyRepo.updateStatus(id, Pharmacy.Status.ACTIVE);
        p.setStatus(Pharmacy.Status.ACTIVE);
        log.info("Admin {} activated pharmacy {}", adminUserId, id);
        auditLogService.log(adminUserId, "PHARMACY_ACTIVATED", "Pharmacy", id);
        return AdminPharmacyDto.from(p);
    }

    @Transactional
    public AdminPharmacyDto suspendPharmacy(UUID id, String adminUserId) {
        Pharmacy p = pharmacyRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Pharmacy", id));
        pharmacyRepo.updateStatus(id, Pharmacy.Status.SUSPENDED);
        p.setStatus(Pharmacy.Status.SUSPENDED);
        log.info("Admin {} suspended pharmacy {}", adminUserId, id);
        auditLogService.log(adminUserId, "PHARMACY_SUSPENDED", "Pharmacy", id);
        return AdminPharmacyDto.from(p);
    }

    public AdminPharmacyStatsDto pharmacyStats(UUID id) {
        Pharmacy p = pharmacyRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Pharmacy", id));
        PharmacyStatsRow row = paymentRepo.pharmacyStats(id);
        return new AdminPharmacyStatsDto(
            id,
            p.getName(),
            row != null ? nullToZeroLong(row.getTotalReservations())     : 0L,
            row != null ? nullToZeroLong(row.getCompletedReservations()) : 0L,
            row != null ? nullToZeroLong(row.getCancelledReservations()) : 0L,
            row != null ? nullToZeroLong(row.getExpiredReservations())   : 0L,
            row != null ? nullToZeroBD(row.getTotalRevenue())    : BigDecimal.ZERO,
            row != null ? nullToZeroBD(row.getTotalCommission()) : BigDecimal.ZERO,
            row != null ? nullToZeroBD(row.getNetRevenue())      : BigDecimal.ZERO,
            p.getRating(),
            p.getReviewCount()
        );
    }

    // ── USERS ─────────────────────────────────────────────────────

    public PageResponse<AdminUserDto> listUsers(
            User.Role role, User.Status status, String search, Pageable pageable) {
        Page<User> page = userRepo.findAdminList(role, status, search, pageable);
        return PageResponse.of(page.map(AdminUserDto::from));
    }

    @Transactional
    public AdminUserDto blockUser(UUID id, String adminUserId) {
        User u = userRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", id));
        userRepo.updateStatus(id, User.Status.SUSPENDED);
        u.setStatus(User.Status.SUSPENDED);
        log.info("Admin {} blocked user {}", adminUserId, id);
        auditLogService.log(adminUserId, "USER_BLOCKED", "User", id);
        return AdminUserDto.from(u);
    }

    @Transactional
    public void deleteUser(UUID id, String adminUserId) {
        if (!userRepo.existsById(id)) {
            throw new ResourceNotFoundException("User", id);
        }
        // Soft-delete: mark INACTIVE rather than hard-delete to preserve audit trail
        userRepo.updateStatus(id, User.Status.INACTIVE);
        log.info("Admin {} deleted (soft) user {}", adminUserId, id);
        auditLogService.log(adminUserId, "USER_DELETED", "User", id);
    }

    // ── TRANSACTIONS ──────────────────────────────────────────────

    public PageResponse<AdminTransactionDto> listTransactions(
            Payment.Status status, Payment.Method method,
            Instant from, Instant to, Pageable pageable) {
        Page<Payment> page = paymentRepo.findAdminTransactions(status, method, from, to, pageable);
        return PageResponse.of(page.map(AdminTransactionDto::from));
    }

    // ── COMMISSIONS ───────────────────────────────────────────────

    public CommissionReportDto commissionReport(Instant from, Instant to) {
        List<CommissionRow> rows = paymentRepo.commissionByPharmacy(from, to);

        BigDecimal totalGross      = BigDecimal.ZERO;
        BigDecimal totalCommission = BigDecimal.ZERO;
        BigDecimal totalNet        = BigDecimal.ZERO;
        long       totalTxns       = 0;

        List<CommissionReportDto.PharmacyRow> dtoRows = new java.util.ArrayList<>();
        for (CommissionRow r : rows) {
            totalGross      = totalGross.add(nullToZeroBD(r.getGrossRevenue()));
            totalCommission = totalCommission.add(nullToZeroBD(r.getCommissionAmount()));
            totalNet        = totalNet.add(nullToZeroBD(r.getNetAmount()));
            totalTxns      += r.getTransactionCount() != null ? r.getTransactionCount() : 0;
            dtoRows.add(new CommissionReportDto.PharmacyRow(
                r.getPharmacyId(),
                r.getPharmacyName(),
                r.getTransactionCount() != null ? r.getTransactionCount() : 0L,
                nullToZeroBD(r.getGrossRevenue()),
                nullToZeroBD(r.getCommissionAmount()),
                nullToZeroBD(r.getNetAmount())
            ));
        }
        return new CommissionReportDto(from, to, totalGross, totalCommission, totalNet, totalTxns, dtoRows);
    }

    // ── REVENUE ───────────────────────────────────────────────────

    public RevenueReportDto revenueReport(Instant from, Instant to) {
        Object[] summary = paymentRepo.revenueSummary(from, to);
        BigDecimal totalRevenue    = summary != null ? asBD(summary[0]) : BigDecimal.ZERO;
        BigDecimal totalCommission = summary != null ? asBD(summary[1]) : BigDecimal.ZERO;
        long       totalTxns       = summary != null ? ((Number) summary[2]).longValue() : 0L;

        // Per-pharmacy revenue using commission rows
        List<CommissionRow> byPharmacy = paymentRepo.commissionByPharmacy(from, to);
        List<RevenueReportDto.PharmacyRow> rows = byPharmacy.stream()
            .map(r -> new RevenueReportDto.PharmacyRow(
                r.getPharmacyId(),
                r.getPharmacyName(),
                r.getTransactionCount() != null ? r.getTransactionCount() : 0L,
                nullToZeroBD(r.getGrossRevenue()),
                nullToZeroBD(r.getCommissionAmount()),
                nullToZeroBD(r.getNetAmount())
            )).toList();

        return new RevenueReportDto(from, to, totalRevenue, totalCommission, totalTxns, rows);
    }

    // ── AUDIT LOGS ────────────────────────────────────────────────

    public PageResponse<AuditLogDto> listAuditLogs(
            String action, String resourceType,
            AuditLog.Status status, Instant from, Instant to, Pageable pageable) {
        Page<AuditLog> page = auditLogRepo.findAdminList(
            action, resourceType, status, from, to, pageable);
        return PageResponse.of(page.map(AuditLogDto::from));
    }

    // ── DASHBOARD STATS ───────────────────────────────────────────

    public DashboardStatsDto dashboardStats() {
        Instant startOfToday = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant startOfMonth = Instant.now()
            .atZone(java.time.ZoneId.of("Africa/Dakar"))
            .withDayOfMonth(1)
            .truncatedTo(ChronoUnit.DAYS)
            .toInstant();

        // Pharmacies
        long totalPharmacies  = pharmacyRepo.count();
        long activePharmacies = pharmacyRepo.countByStatus(Pharmacy.Status.ACTIVE);
        long pendingApproval  = pharmacyRepo.countByStatus(Pharmacy.Status.PENDING_APPROVAL);
        long suspended        = pharmacyRepo.countByStatus(Pharmacy.Status.SUSPENDED);

        // Users
        long totalUsers     = userRepo.count();
        long totalCustomers = userRepo.countByRole(User.Role.CUSTOMER);
        long blockedUsers   = userRepo.countByStatus(User.Status.SUSPENDED);

        // Reservations
        long resToday = paymentRepo.countReservationsFrom(startOfToday);
        long resMonth = paymentRepo.countReservationsFrom(startOfMonth);

        // Revenue
        Object[] today = paymentRepo.revenueFrom(startOfToday);
        Object[] month = paymentRepo.revenueFrom(startOfMonth);

        return new DashboardStatsDto(
            totalPharmacies, activePharmacies, pendingApproval, suspended,
            totalUsers, totalCustomers, blockedUsers,
            resToday, resMonth,
            asBD(today[0]), asBD(month[0]),
            asBD(today[1]), asBD(month[1])
        );
    }

    // ── Helpers ───────────────────────────────────────────────────

    private static BigDecimal nullToZeroBD(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static long nullToZeroLong(Long v) {
        return v != null ? v : 0L;
    }

    private static BigDecimal asBD(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        return new BigDecimal(o.toString());
    }
}
