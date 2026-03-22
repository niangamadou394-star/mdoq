package com.medoq.backend.stock;

import com.medoq.backend.dto.stock.StockAlertDto;
import com.medoq.backend.dto.stock.StockImportResultDto;
import com.medoq.backend.dto.stock.StockItemDto;
import com.medoq.backend.dto.stock.UpdateStockRequest;
import com.medoq.backend.entity.*;
import com.medoq.backend.exception.BusinessException;
import com.medoq.backend.repository.*;
import com.medoq.backend.service.NotificationService;
import com.medoq.backend.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock PharmacyRepository      pharmacyRepo;
    @Mock PharmacyStockRepository stockRepo;
    @Mock MedicationRepository    medicationRepo;
    @Mock StockMovementRepository movementRepo;
    @Mock NotificationService     notificationService;

    @InjectMocks StockService stockService;

    private UUID   pharmacyId;
    private UUID   medicationId;
    private UUID   ownerId;
    private String ownerIdStr;

    private User         owner;
    private Pharmacy     pharmacy;
    private Medication   medication;
    private PharmacyStock stock;

    @BeforeEach
    void setUp() {
        pharmacyId   = UUID.randomUUID();
        medicationId = UUID.randomUUID();
        ownerId      = UUID.randomUUID();
        ownerIdStr   = ownerId.toString();

        owner = User.builder()
            .id(ownerId)
            .firstName("Aminata").lastName("Diallo")
            .phone("+221771234567")
            .role(User.Role.PHARMACY_OWNER)
            .status(User.Status.ACTIVE)
            .build();

        pharmacy = Pharmacy.builder()
            .id(pharmacyId)
            .name("Pharmacie du Plateau")
            .licenseNumber("PH-2024-001")
            .city("Dakar").region("Dakar")
            .phone("+221338201234")
            .status(Pharmacy.Status.ACTIVE)
            .rating(BigDecimal.valueOf(4.0))
            .reviewCount(0)
            .owner(owner)
            .build();

        medication = Medication.builder()
            .id(medicationId)
            .name("Paracétamol 500mg")
            .genericName("Paracétamol")
            .category("Analgésique")
            .dosageForm("Comprimé")
            .strength("500mg")
            .requiresPrescription(false)
            .build();

        stock = PharmacyStock.builder()
            .id(UUID.randomUUID())
            .pharmacy(pharmacy)
            .medication(medication)
            .quantity(15)
            .reorderLevel(10)
            .unitPrice(BigDecimal.valueOf(150))
            .isAvailable(true)
            .build();
    }

    // ── 1. Alert triggered at threshold ──────────────────────────

    @Test
    @DisplayName("updateStock triggers STOCK_ALERT when quantity == reorderLevel")
    void updateStock_atThreshold_triggersAlert() {
        when(pharmacyRepo.findById(pharmacyId)).thenReturn(Optional.of(pharmacy));
        when(stockRepo.findByPharmacyIdAndMedicationId(pharmacyId, medicationId))
            .thenReturn(Optional.of(stock));
        when(stockRepo.save(any())).thenReturn(stock);
        when(movementRepo.save(any())).thenReturn(mock(StockMovement.class));

        // Set quantity exactly at the reorder level (10)
        UpdateStockRequest req = new UpdateStockRequest(10, null, null, null, null);
        stockService.updateStock(pharmacyId, medicationId, req, ownerIdStr);

        verify(notificationService).sendStockAlert(stock);
    }

    @Test
    @DisplayName("updateStock triggers STOCK_ALERT when quantity < reorderLevel")
    void updateStock_belowThreshold_triggersAlert() {
        when(pharmacyRepo.findById(pharmacyId)).thenReturn(Optional.of(pharmacy));
        when(stockRepo.findByPharmacyIdAndMedicationId(pharmacyId, medicationId))
            .thenReturn(Optional.of(stock));
        when(stockRepo.save(any())).thenReturn(stock);
        when(movementRepo.save(any())).thenReturn(mock(StockMovement.class));

        // Set quantity below threshold
        UpdateStockRequest req = new UpdateStockRequest(5, null, null, null, null);
        stockService.updateStock(pharmacyId, medicationId, req, ownerIdStr);

        verify(notificationService).sendStockAlert(stock);
    }

    @Test
    @DisplayName("updateStock does NOT trigger alert when quantity > reorderLevel")
    void updateStock_aboveThreshold_noAlert() {
        when(pharmacyRepo.findById(pharmacyId)).thenReturn(Optional.of(pharmacy));
        when(stockRepo.findByPharmacyIdAndMedicationId(pharmacyId, medicationId))
            .thenReturn(Optional.of(stock));
        when(stockRepo.save(any())).thenReturn(stock);
        when(movementRepo.save(any())).thenReturn(mock(StockMovement.class));

        // Plenty of stock — above the reorder level (10)
        UpdateStockRequest req = new UpdateStockRequest(50, null, null, null, null);
        stockService.updateStock(pharmacyId, medicationId, req, ownerIdStr);

        verify(notificationService, never()).sendStockAlert(any());
    }

    // ── 2. Movement history recorded ─────────────────────────────

    @Test
    @DisplayName("updateStock saves StockMovement with correct before/after/delta")
    void updateStock_recordsMovementHistory() {
        when(pharmacyRepo.findById(pharmacyId)).thenReturn(Optional.of(pharmacy));
        when(stockRepo.findByPharmacyIdAndMedicationId(pharmacyId, medicationId))
            .thenReturn(Optional.of(stock));   // stock.quantity = 15
        when(stockRepo.save(any())).thenReturn(stock);
        when(movementRepo.save(any())).thenReturn(mock(StockMovement.class));

        UpdateStockRequest req = new UpdateStockRequest(80, null, null, null, "Commande fournisseur");
        stockService.updateStock(pharmacyId, medicationId, req, ownerIdStr);

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(movementRepo).save(captor.capture());

        StockMovement saved = captor.getValue();
        assertThat(saved.getQuantityBefore()).isEqualTo(15);
        assertThat(saved.getQuantityAfter()).isEqualTo(80);
        assertThat(saved.getDelta()).isEqualTo(65);
        assertThat(saved.getReason()).isEqualTo(StockMovement.Reason.MANUAL_UPDATE);
        assertThat(saved.getActorUserId()).isEqualTo(ownerIdStr);
        assertThat(saved.getNote()).isEqualTo("Commande fournisseur");
    }

    @Test
    @DisplayName("batchUpdate saves one StockMovement per updated row")
    void batchUpdate_recordsMovementPerRow() {
        String csv = "medicationId,quantity,reorderLevel\n"
            + medicationId + ",100,15\n";
        MockMultipartFile file = new MockMultipartFile(
            "file", "batch.csv", "text/csv",
            csv.getBytes(StandardCharsets.UTF_8));

        when(pharmacyRepo.findById(pharmacyId)).thenReturn(Optional.of(pharmacy));
        when(stockRepo.findByPharmacyIdAndMedicationId(pharmacyId, medicationId))
            .thenReturn(Optional.of(stock));   // stock.quantity = 15
        when(stockRepo.save(any())).thenReturn(stock);
        when(movementRepo.save(any())).thenReturn(mock(StockMovement.class));

        StockImportResultDto result = stockService.batchUpdate(pharmacyId, file, ownerIdStr);

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.errors()).isEmpty();
        verify(movementRepo, times(1)).save(argThat(m ->
            m.getReason() == StockMovement.Reason.BATCH_UPDATE));
    }

    // ── 3. CSV import ─────────────────────────────────────────────

    @Test
    @DisplayName("importCatalogue creates new medication and stock entry")
    void importCatalogue_createsNewMedicationAndStock() {
        String csv = "name,genericName,barcode,category,dosageForm,strength,"
            + "requiresPrescription,unitPrice,quantity,reorderLevel,expiryDate\n"
            + "Ibuprofène 400mg,Ibuprofène,6165000099,AINS,Comprimé,400mg,"
            + "false,200,50,10,2027-06-30\n";
        MockMultipartFile file = new MockMultipartFile(
            "file", "catalogue.csv", "text/csv",
            csv.getBytes(StandardCharsets.UTF_8));

        UUID newMedId = UUID.randomUUID();
        Medication newMed = Medication.builder()
            .id(newMedId).name("Ibuprofène 400mg")
            .genericName("Ibuprofène").barcode("6165000099")
            .build();

        when(pharmacyRepo.findById(pharmacyId)).thenReturn(Optional.of(pharmacy));
        when(pharmacyRepo.getReferenceById(pharmacyId)).thenReturn(pharmacy);
        when(medicationRepo.findByBarcode("6165000099")).thenReturn(Optional.empty());
        when(medicationRepo.save(any(Medication.class))).thenReturn(newMed);
        when(stockRepo.findByPharmacyIdAndMedicationId(pharmacyId, newMedId))
            .thenReturn(Optional.empty());
        when(stockRepo.save(any(PharmacyStock.class))).thenAnswer(inv -> inv.getArgument(0));
        when(movementRepo.save(any())).thenReturn(mock(StockMovement.class));

        StockImportResultDto result = stockService.importCatalogue(pharmacyId, file, ownerIdStr);

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isZero();
        assertThat(result.errors()).isEmpty();

        verify(medicationRepo).save(argThat(m ->
            "Ibuprofène 400mg".equals(m.getName()) &&
            "6165000099".equals(m.getBarcode())));
        verify(stockRepo).save(argThat(s ->
            s.getQuantity() == 50 &&
            s.getReorderLevel() == 10 &&
            s.getUnitPrice().compareTo(BigDecimal.valueOf(200)) == 0));
    }

    @Test
    @DisplayName("importCatalogue updates existing stock entry when medication found by barcode")
    void importCatalogue_updatesExistingStock() {
        String csv = "name,genericName,barcode,category,dosageForm,strength,"
            + "requiresPrescription,unitPrice,quantity,reorderLevel\n"
            + "Paracétamol 500mg,Paracétamol,6165000001,Analgésique,Comprimé,500mg,"
            + "false,150,200,20\n";
        MockMultipartFile file = new MockMultipartFile(
            "file", "catalogue.csv", "text/csv",
            csv.getBytes(StandardCharsets.UTF_8));

        medication.setBarcode("6165000001");

        when(pharmacyRepo.findById(pharmacyId)).thenReturn(Optional.of(pharmacy));
        when(medicationRepo.findByBarcode("6165000001")).thenReturn(Optional.of(medication));
        when(medicationRepo.save(any())).thenReturn(medication);
        when(stockRepo.findByPharmacyIdAndMedicationId(pharmacyId, medicationId))
            .thenReturn(Optional.of(stock));   // existing stock qty=15
        when(stockRepo.save(any())).thenReturn(stock);
        when(movementRepo.save(any())).thenReturn(mock(StockMovement.class));

        StockImportResultDto result = stockService.importCatalogue(pharmacyId, file, ownerIdStr);

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.created()).isZero();
        assertThat(result.errors()).isEmpty();

        // Stock quantity should have been set to 200
        verify(stockRepo).save(argThat(s -> s.getQuantity() == 200));
    }

    @Test
    @DisplayName("importCatalogue skips rows with invalid data and reports errors")
    void importCatalogue_skipsInvalidRows() {
        String csv = "name,genericName,barcode,category,dosageForm,strength,"
            + "requiresPrescription,unitPrice,quantity\n"
            + ",Paracétamol,,,,,,not-a-number,50\n";   // blank name + invalid price
        MockMultipartFile file = new MockMultipartFile(
            "file", "catalogue.csv", "text/csv",
            csv.getBytes(StandardCharsets.UTF_8));

        when(pharmacyRepo.findById(pharmacyId)).thenReturn(Optional.of(pharmacy));

        StockImportResultDto result = stockService.importCatalogue(pharmacyId, file, ownerIdStr);

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
        verifyNoInteractions(medicationRepo, stockRepo, movementRepo);
    }

    // ── 4. Ownership validation ───────────────────────────────────

    @Test
    @DisplayName("updateStock throws BusinessException when caller is not the pharmacy owner")
    void updateStock_wrongOwner_throws() {
        when(pharmacyRepo.findById(pharmacyId)).thenReturn(Optional.of(pharmacy));

        UpdateStockRequest req = new UpdateStockRequest(100, null, null, null, null);

        assertThatThrownBy(() -> stockService.updateStock(
                pharmacyId, medicationId, req, UUID.randomUUID().toString()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("propriétaire");
    }

    // ── 5. Alert list returns suggested order qty ─────────────────

    @Test
    @DisplayName("listAlerts computes suggestedOrderQty from 30-day consumption")
    void listAlerts_computesSuggestion() {
        // Stock below threshold: qty=5, reorderLevel=10
        stock.setQuantity(5);

        when(pharmacyRepo.findById(pharmacyId)).thenReturn(Optional.of(pharmacy));
        when(stockRepo.findAlerts(pharmacyId)).thenReturn(List.of(stock));
        // 90 units consumed in 30 days → avg 3/day → need 90 - 5 + 10 = 95
        when(movementRepo.totalConsumedSince(eq(stock.getId()), any(Instant.class)))
            .thenReturn(90L);

        List<StockAlertDto> alerts = stockService.listAlerts(pharmacyId, ownerIdStr);

        assertThat(alerts).hasSize(1);
        StockAlertDto alert = alerts.get(0);
        assertThat(alert.currentQuantity()).isEqualTo(5);
        assertThat(alert.reorderLevel()).isEqualTo(10);
        // avg=3/day, need=3*30=90, gap=90-5+10=95 → max(10, 95) = 95
        assertThat(alert.suggestedOrderQty()).isEqualTo(95);
    }

    @Test
    @DisplayName("listAlerts uses default suggestion when no history exists")
    void listAlerts_noHistory_usesDefaultSuggestion() {
        stock.setQuantity(3);

        when(pharmacyRepo.findById(pharmacyId)).thenReturn(Optional.of(pharmacy));
        when(stockRepo.findAlerts(pharmacyId)).thenReturn(List.of(stock));
        when(movementRepo.totalConsumedSince(any(), any())).thenReturn(0L);

        List<StockAlertDto> alerts = stockService.listAlerts(pharmacyId, ownerIdStr);

        assertThat(alerts).hasSize(1);
        // No history → default = reorderLevel * 2 = 20
        assertThat(alerts.get(0).suggestedOrderQty()).isEqualTo(20);
    }
}
