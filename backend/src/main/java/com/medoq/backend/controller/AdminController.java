package com.medoq.backend.controller;

import com.medoq.backend.dto.admin.*;
import com.medoq.backend.entity.AuditLog;
import com.medoq.backend.entity.Payment;
import com.medoq.backend.entity.Pharmacy;
import com.medoq.backend.entity.User;
import com.medoq.backend.service.AdminService;
import com.medoq.backend.util.CsvExporter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Back-office API — all endpoints require ROLE_ADMIN.
 *
 * Context path : /api/v1
 * Full prefix  : /api/v1/admin/**
 *
 * Pagination defaults : page=0, size=20, sorted by createdAt DESC.
 * CSV export          : append ?format=csv to financial report endpoints.
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");

    private final AdminService adminService;

    // ══════════════════════════════════════════════════════════════
    // DASHBOARD
    // ══════════════════════════════════════════════════════════════

    /**
     * GET /admin/stats
     * Returns global KPIs: pharmacy counts, user counts,
     * reservations and revenue for today and current month.
     */
    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsDto> stats() {
        return ResponseEntity.ok(adminService.dashboardStats());
    }

    // ══════════════════════════════════════════════════════════════
    // PHARMACIES
    // ══════════════════════════════════════════════════════════════

    /**
     * GET /admin/pharmacies?status=&city=&search=&page=&size=
     */
    @GetMapping("/pharmacies")
    public ResponseEntity<PageResponse<AdminPharmacyDto>> listPharmacies(
            @RequestParam(required = false) Pharmacy.Status status,
            @RequestParam(required = false) String          city,
            @RequestParam(required = false) String          search,
            @RequestParam(defaultValue = "0")  int          page,
            @RequestParam(defaultValue = "20") int          size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(adminService.listPharmacies(status, city, search, pageable));
    }

    /**
     * POST /admin/pharmacies/{id}/activate
     * Sets pharmacy status to ACTIVE.
     */
    @PostMapping("/pharmacies/{id}/activate")
    public ResponseEntity<AdminPharmacyDto> activatePharmacy(
            @PathVariable UUID id,
            @AuthenticationPrincipal String adminId) {
        return ResponseEntity.ok(adminService.activatePharmacy(id, adminId));
    }

    /**
     * POST /admin/pharmacies/{id}/suspend
     * Sets pharmacy status to SUSPENDED.
     */
    @PostMapping("/pharmacies/{id}/suspend")
    public ResponseEntity<AdminPharmacyDto> suspendPharmacy(
            @PathVariable UUID id,
            @AuthenticationPrincipal String adminId) {
        return ResponseEntity.ok(adminService.suspendPharmacy(id, adminId));
    }

    /**
     * GET /admin/pharmacies/{id}/stats
     * Returns reservation counts and revenue stats for a specific pharmacy.
     */
    @GetMapping("/pharmacies/{id}/stats")
    public ResponseEntity<AdminPharmacyStatsDto> pharmacyStats(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.pharmacyStats(id));
    }

    // ══════════════════════════════════════════════════════════════
    // USERS
    // ══════════════════════════════════════════════════════════════

    /**
     * GET /admin/users?role=&status=&search=&page=&size=
     */
    @GetMapping("/users")
    public ResponseEntity<PageResponse<AdminUserDto>> listUsers(
            @RequestParam(required = false) User.Role   role,
            @RequestParam(required = false) User.Status status,
            @RequestParam(required = false) String      search,
            @RequestParam(defaultValue = "0")  int      page,
            @RequestParam(defaultValue = "20") int      size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(adminService.listUsers(role, status, search, pageable));
    }

    /**
     * POST /admin/users/{id}/block
     * Sets user status to SUSPENDED.
     */
    @PostMapping("/users/{id}/block")
    public ResponseEntity<AdminUserDto> blockUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal String adminId) {
        return ResponseEntity.ok(adminService.blockUser(id, adminId));
    }

    /**
     * DELETE /admin/users/{id}
     * Soft-deletes the user (sets status to INACTIVE) to preserve audit trail.
     */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal String adminId) {
        adminService.deleteUser(id, adminId);
        return ResponseEntity.noContent().build();
    }

    // ══════════════════════════════════════════════════════════════
    // TRANSACTIONS
    // ══════════════════════════════════════════════════════════════

    /**
     * GET /admin/transactions?status=&method=&from=&to=&page=&size=&format=csv
     *
     * Dates: ISO-8601 format (e.g. 2026-01-01T00:00:00Z).
     * Add ?format=csv to download as CSV attachment.
     */
    @GetMapping("/transactions")
    public void listTransactions(
            @RequestParam(required = false) Payment.Status status,
            @RequestParam(required = false) Payment.Method method,
            @RequestParam(required = false) Instant        from,
            @RequestParam(required = false) Instant        to,
            @RequestParam(defaultValue = "0")   int        page,
            @RequestParam(defaultValue = "100") int        size,
            @RequestParam(defaultValue = "json") String    format,
            HttpServletResponse response) throws IOException {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        PageResponse<AdminTransactionDto> result =
            adminService.listTransactions(status, method, from, to, pageable);

        if ("csv".equalsIgnoreCase(format)) {
            String csv = CsvExporter.transactions(result.content());
            writeCsv(response, csv, "transactions.csv");
        } else {
            writeJson(response, result);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // COMMISSIONS
    // ══════════════════════════════════════════════════════════════

    /**
     * GET /admin/commissions?from=&to=&format=csv
     *
     * Returns per-pharmacy commission breakdown for the given period.
     * Defaults to the current calendar month if no dates are provided.
     * Add ?format=csv for CSV download.
     */
    @GetMapping("/commissions")
    public void commissions(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "json") String format,
            HttpServletResponse response) throws IOException {

        Instant[] range = defaultMonthRange(from, to);
        CommissionReportDto report = adminService.commissionReport(range[0], range[1]);

        if ("csv".equalsIgnoreCase(format)) {
            String csv = CsvExporter.commissions(report);
            writeCsv(response, csv, "commissions.csv");
        } else {
            writeJson(response, report);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // REVENUE
    // ══════════════════════════════════════════════════════════════

    /**
     * GET /admin/revenue?from=&to=
     *
     * Returns total CA + per-pharmacy breakdown for the given period.
     */
    @GetMapping("/revenue")
    public ResponseEntity<RevenueReportDto> revenue(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        Instant[] range = defaultMonthRange(from, to);
        return ResponseEntity.ok(adminService.revenueReport(range[0], range[1]));
    }

    // ══════════════════════════════════════════════════════════════
    // AUDIT LOGS
    // ══════════════════════════════════════════════════════════════

    /**
     * GET /admin/audit-logs?action=&resourceType=&status=&from=&to=&page=&size=
     */
    @GetMapping("/audit-logs")
    public ResponseEntity<PageResponse<AuditLogDto>> auditLogs(
            @RequestParam(required = false) String         action,
            @RequestParam(required = false) String         resourceType,
            @RequestParam(required = false) AuditLog.Status status,
            @RequestParam(required = false) Instant        from,
            @RequestParam(required = false) Instant        to,
            @RequestParam(defaultValue = "0")   int        page,
            @RequestParam(defaultValue = "50")  int        size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        return ResponseEntity.ok(
            adminService.listAuditLogs(action, resourceType, status, from, to, pageable));
    }

    // ══════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════

    /** Default to current calendar month in Dakar timezone when no dates given. */
    private Instant[] defaultMonthRange(Instant from, Instant to) {
        if (from == null) {
            from = LocalDate.now(DAKAR).withDayOfMonth(1)
                .atStartOfDay(DAKAR).toInstant();
        }
        if (to == null) {
            to = Instant.now();
        }
        return new Instant[]{from, to};
    }

    private void writeCsv(HttpServletResponse response, String csv, String filename)
            throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        // UTF-8 BOM for Excel compatibility
        response.getWriter().write('\uFEFF');
        response.getWriter().write(csv);
    }

    private void writeJson(HttpServletResponse response, Object body) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        new com.fasterxml.jackson.databind.ObjectMapper()
            .findAndRegisterModules()
            .writeValue(response.getWriter(), body);
    }
}
