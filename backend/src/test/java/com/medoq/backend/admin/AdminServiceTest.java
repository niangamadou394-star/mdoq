package com.medoq.backend.admin;

import com.medoq.backend.dto.admin.*;
import com.medoq.backend.entity.*;
import com.medoq.backend.exception.ResourceNotFoundException;
import com.medoq.backend.repository.*;
import com.medoq.backend.service.AdminService;
import com.medoq.backend.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock PharmacyRepository    pharmacyRepo;
    @Mock UserRepository        userRepo;
    @Mock PaymentRepository     paymentRepo;
    @Mock ReservationRepository reservationRepo;
    @Mock AuditLogRepository    auditLogRepo;
    @Mock AuditLogService       auditLogService;

    @InjectMocks
    AdminService adminService;

    private UUID pharmacyId;
    private UUID userId;
    private Pharmacy pharmacy;
    private User     owner;
    private User     customer;

    @BeforeEach
    void setUp() {
        pharmacyId = UUID.randomUUID();
        userId     = UUID.randomUUID();

        owner = User.builder()
            .id(UUID.randomUUID()).firstName("Aminata").lastName("Diallo")
            .phone("+221771234567").role(User.Role.PHARMACY_OWNER)
            .status(User.Status.ACTIVE).build();

        pharmacy = Pharmacy.builder()
            .id(pharmacyId).name("Pharmacie du Plateau")
            .licenseNumber("PH-2024-001").city("Dakar").region("Dakar")
            .phone("+221338201234").status(Pharmacy.Status.PENDING_APPROVAL)
            .rating(BigDecimal.valueOf(4.5)).reviewCount(12)
            .owner(owner).build();

        customer = User.builder()
            .id(userId).firstName("Amadou").lastName("Niang")
            .phone("+221777654321").role(User.Role.CUSTOMER)
            .status(User.Status.ACTIVE).build();
    }

    // ── Pharmacies ────────────────────────────────────────────────

    @Test
    @DisplayName("listPharmacies returns mapped page")
    void listPharmacies_returnsMappedPage() {
        Page<Pharmacy> page = new PageImpl<>(List.of(pharmacy));
        when(pharmacyRepo.findAdminList(null, null, null, PageRequest.of(0, 20)))
            .thenReturn(page);

        PageResponse<AdminPharmacyDto> result =
            adminService.listPharmacies(null, null, null, PageRequest.of(0, 20));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).name()).isEqualTo("Pharmacie du Plateau");
        assertThat(result.content().get(0).status()).isEqualTo(Pharmacy.Status.PENDING_APPROVAL);
    }

    @Test
    @DisplayName("activatePharmacy changes status to ACTIVE and logs audit")
    void activatePharmacy_updatesStatus() {
        when(pharmacyRepo.findById(pharmacyId)).thenReturn(Optional.of(pharmacy));
        when(pharmacyRepo.updateStatus(pharmacyId, Pharmacy.Status.ACTIVE)).thenReturn(1);

        AdminPharmacyDto result = adminService.activatePharmacy(pharmacyId, "admin-123");

        assertThat(result.status()).isEqualTo(Pharmacy.Status.ACTIVE);
        verify(pharmacyRepo).updateStatus(pharmacyId, Pharmacy.Status.ACTIVE);
        verify(auditLogService).log(eq("admin-123"), eq("PHARMACY_ACTIVATED"),
            eq("Pharmacy"), eq(pharmacyId));
    }

    @Test
    @DisplayName("suspendPharmacy changes status to SUSPENDED and logs audit")
    void suspendPharmacy_updatesStatus() {
        when(pharmacyRepo.findById(pharmacyId)).thenReturn(Optional.of(pharmacy));
        when(pharmacyRepo.updateStatus(pharmacyId, Pharmacy.Status.SUSPENDED)).thenReturn(1);

        AdminPharmacyDto result = adminService.suspendPharmacy(pharmacyId, "admin-123");

        assertThat(result.status()).isEqualTo(Pharmacy.Status.SUSPENDED);
        verify(auditLogService).log(eq("admin-123"), eq("PHARMACY_SUSPENDED"),
            eq("Pharmacy"), eq(pharmacyId));
    }

    @Test
    @DisplayName("activatePharmacy throws ResourceNotFoundException for unknown id")
    void activatePharmacy_unknownId_throws() {
        when(pharmacyRepo.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.activatePharmacy(UUID.randomUUID(), "admin"))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Users ─────────────────────────────────────────────────────

    @Test
    @DisplayName("blockUser sets SUSPENDED and logs audit")
    void blockUser_setsStatus() {
        when(userRepo.findById(userId)).thenReturn(Optional.of(customer));
        when(userRepo.updateStatus(userId, User.Status.SUSPENDED)).thenReturn(1);

        AdminUserDto result = adminService.blockUser(userId, "admin-abc");

        assertThat(result.status()).isEqualTo(User.Status.SUSPENDED);
        verify(auditLogService).log(eq("admin-abc"), eq("USER_BLOCKED"),
            eq("User"), eq(userId));
    }

    @Test
    @DisplayName("deleteUser soft-deletes by setting INACTIVE")
    void deleteUser_softDelete() {
        when(userRepo.existsById(userId)).thenReturn(true);

        adminService.deleteUser(userId, "admin-abc");

        verify(userRepo).updateStatus(userId, User.Status.INACTIVE);
        verify(auditLogService).log(eq("admin-abc"), eq("USER_DELETED"),
            eq("User"), eq(userId));
    }

    @Test
    @DisplayName("deleteUser throws ResourceNotFoundException for unknown user")
    void deleteUser_unknownId_throws() {
        when(userRepo.existsById(any())).thenReturn(false);

        assertThatThrownBy(() -> adminService.deleteUser(UUID.randomUUID(), "admin"))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Commission report ─────────────────────────────────────────

    @Test
    @DisplayName("commissionReport returns empty report when no transactions")
    void commissionReport_empty() {
        when(paymentRepo.commissionByPharmacy(any(), any())).thenReturn(List.of());

        CommissionReportDto report =
            adminService.commissionReport(Instant.now().minusSeconds(86400), Instant.now());

        assertThat(report.rows()).isEmpty();
        assertThat(report.totalGross()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(report.totalCommission()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── Dashboard stats ───────────────────────────────────────────

    @Test
    @DisplayName("dashboardStats aggregates all counts correctly")
    void dashboardStats_aggregation() {
        when(pharmacyRepo.count()).thenReturn(10L);
        when(pharmacyRepo.countByStatus(Pharmacy.Status.ACTIVE)).thenReturn(7L);
        when(pharmacyRepo.countByStatus(Pharmacy.Status.PENDING_APPROVAL)).thenReturn(2L);
        when(pharmacyRepo.countByStatus(Pharmacy.Status.SUSPENDED)).thenReturn(1L);
        when(userRepo.count()).thenReturn(500L);
        when(userRepo.countByRole(User.Role.CUSTOMER)).thenReturn(480L);
        when(userRepo.countByStatus(User.Status.SUSPENDED)).thenReturn(3L);
        when(paymentRepo.countReservationsFrom(any())).thenReturn(15L);
        when(paymentRepo.revenueFrom(any()))
            .thenReturn(new Object[]{new BigDecimal("150000"), new BigDecimal("2250")});

        DashboardStatsDto stats = adminService.dashboardStats();

        assertThat(stats.totalPharmacies()).isEqualTo(10L);
        assertThat(stats.activePharmacies()).isEqualTo(7L);
        assertThat(stats.pendingApproval()).isEqualTo(2L);
        assertThat(stats.totalUsers()).isEqualTo(500L);
        assertThat(stats.totalCustomers()).isEqualTo(480L);
        assertThat(stats.revenueToday()).isEqualByComparingTo(new BigDecimal("150000"));
        assertThat(stats.commissionToday()).isEqualByComparingTo(new BigDecimal("2250"));
    }
}
