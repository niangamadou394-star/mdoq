package com.medoq.backend.service;

import com.medoq.backend.dto.admin.PageResponse;
import com.medoq.backend.dto.stock.*;
import com.medoq.backend.entity.*;
import com.medoq.backend.exception.BusinessException;
import com.medoq.backend.exception.ResourceNotFoundException;
import com.medoq.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockService {

    private final PharmacyRepository      pharmacyRepo;
    private final PharmacyStockRepository stockRepo;
    private final MedicationRepository    medicationRepo;
    private final StockMovementRepository movementRepo;
    private final NotificationService     notificationService;

    // ── LIST ──────────────────────────────────────────────────────

    public PageResponse<StockItemDto> listStock(
            UUID pharmacyId, String search, String ownerId, Pageable pageable) {
        validateOwnership(pharmacyId, ownerId);
        Page<PharmacyStock> page = stockRepo.findByPharmacyId(pharmacyId, search, pageable);
        return PageResponse.of(page.map(StockItemDto::from));
    }

    // ── UPDATE ONE ────────────────────────────────────────────────

    @Transactional
    public StockItemDto updateStock(UUID pharmacyId, UUID medicationId,
                                    UpdateStockRequest req, String ownerId) {
        validateOwnership(pharmacyId, ownerId);
        PharmacyStock stock = stockRepo.findByPharmacyIdAndMedicationId(pharmacyId, medicationId)
            .orElseThrow(() -> new ResourceNotFoundException("PharmacyStock", medicationId));

        int before = stock.getQuantity();
        stock.setQuantity(req.quantity());
        if (req.reorderLevel() != null)   stock.setReorderLevel(req.reorderLevel());
        if (req.unitPrice()    != null)   stock.setUnitPrice(req.unitPrice());
        if (req.expiryDate()   != null)   stock.setExpiryDate(req.expiryDate());
        stock.setAvailable(req.quantity() > 0);
        if (req.quantity() > before)      stock.setLastRestocked(Instant.now());

        stockRepo.save(stock);
        recordMovement(stock, before, req.quantity(), ownerId,
            StockMovement.Reason.MANUAL_UPDATE, req.note());
        checkAndAlert(stock);

        log.info("Stock updated: pharmacy={} medication={} qty {}→{} by {}",
            pharmacyId, medicationId, before, req.quantity(), ownerId);
        return StockItemDto.from(stock);
    }

    // ── BATCH UPDATE FROM CSV ─────────────────────────────────────

    /**
     * CSV format (with header): medicationId,quantity,reorderLevel
     * - reorderLevel is optional (column may be omitted or left blank)
     */
    @Transactional
    public StockImportResultDto batchUpdate(UUID pharmacyId, MultipartFile file, String ownerId) {
        validateOwnership(pharmacyId, ownerId);
        List<String> errors = new ArrayList<>();
        int created = 0, updated = 0, skipped = 0, row = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isBlank()) continue;
                if (header) { header = false; continue; }   // skip header row
                row++;

                String[] parts = line.split(",", -1);
                if (parts.length < 2) {
                    errors.add("Ligne " + row + ": colonnes insuffisantes (minimum: medicationId,quantity)");
                    skipped++;
                    continue;
                }

                try {
                    UUID   medId = UUID.fromString(parts[0].trim());
                    int    qty   = Integer.parseInt(parts[1].trim());
                    Integer reorder = parts.length >= 3 && !parts[2].isBlank()
                        ? Integer.parseInt(parts[2].trim()) : null;

                    if (qty < 0) throw new NumberFormatException("quantité négative");

                    Optional<PharmacyStock> existing =
                        stockRepo.findByPharmacyIdAndMedicationId(pharmacyId, medId);

                    if (existing.isPresent()) {
                        PharmacyStock stock = existing.get();
                        int before = stock.getQuantity();
                        stock.setQuantity(qty);
                        if (reorder != null) stock.setReorderLevel(reorder);
                        stock.setAvailable(qty > 0);
                        if (qty > before) stock.setLastRestocked(Instant.now());
                        stockRepo.save(stock);
                        recordMovement(stock, before, qty, ownerId,
                            StockMovement.Reason.BATCH_UPDATE, "Batch CSV");
                        checkAndAlert(stock);
                        updated++;
                    } else {
                        // Medication must already exist in catalogue
                        medicationRepo.findById(medId).ifPresentOrElse(med -> {
                            // handled below
                        }, () -> {
                            throw new NoSuchElementException("Médicament introuvable: " + medId);
                        });
                        // Create new stock entry
                        Pharmacy pharmacy = pharmacyRepo.getReferenceById(pharmacyId);
                        Medication med    = medicationRepo.getReferenceById(medId);
                        PharmacyStock stock = PharmacyStock.builder()
                            .pharmacy(pharmacy)
                            .medication(med)
                            .quantity(qty)
                            .reorderLevel(reorder != null ? reorder : 10)
                            .unitPrice(BigDecimal.ZERO)
                            .isAvailable(qty > 0)
                            .build();
                        stockRepo.save(stock);
                        recordMovement(stock, 0, qty, ownerId,
                            StockMovement.Reason.BATCH_UPDATE, "Batch CSV (création)");
                        checkAndAlert(stock);
                        created++;
                    }
                } catch (IllegalArgumentException | NoSuchElementException e) {
                    errors.add("Ligne " + row + ": " + e.getMessage());
                    skipped++;
                }
            }
        } catch (Exception e) {
            throw new BusinessException("Erreur lecture CSV: " + e.getMessage());
        }

        log.info("Batch update pharmacy={}: created={} updated={} skipped={} errors={}",
            pharmacyId, created, updated, skipped, errors.size());
        return new StockImportResultDto(row, created, updated, skipped, errors);
    }

    // ── ALERTS ────────────────────────────────────────────────────

    public List<StockAlertDto> listAlerts(UUID pharmacyId, String ownerId) {
        validateOwnership(pharmacyId, ownerId);
        List<PharmacyStock> alerts = stockRepo.findAlerts(pharmacyId);
        Instant since30d = Instant.now().minus(30, ChronoUnit.DAYS);

        return alerts.stream().map(stock -> {
            Long consumed = movementRepo.totalConsumedSince(stock.getId(), since30d);
            int suggested = computeSuggested(stock, consumed != null ? consumed : 0L);
            return new StockAlertDto(
                stock.getId(),
                stock.getMedication().getId(),
                stock.getMedication().getName(),
                stock.getMedication().getGenericName(),
                stock.getQuantity(),
                stock.getReorderLevel(),
                stock.getUnitPrice(),
                suggested
            );
        }).toList();
    }

    // ── CATALOGUE IMPORT FROM CSV ──────────────────────────────────

    /**
     * CSV format (with header):
     * name,genericName,barcode,category,dosageForm,strength,requiresPrescription,
     * unitPrice,quantity,reorderLevel,expiryDate
     *
     * Upserts medication by barcode (if provided) or exact name match.
     * Creates or updates the PharmacyStock entry for this pharmacy.
     */
    @Transactional
    public StockImportResultDto importCatalogue(UUID pharmacyId, MultipartFile file, String ownerId) {
        validateOwnership(pharmacyId, ownerId);
        Pharmacy pharmacy = pharmacyRepo.getReferenceById(pharmacyId);

        List<String> errors = new ArrayList<>();
        int created = 0, updated = 0, skipped = 0, row = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                // Strip UTF-8 BOM if present
                if (header && line.startsWith("\uFEFF")) line = line.substring(1);
                line = line.trim();
                if (line.isBlank()) continue;
                if (header) { header = false; continue; }
                row++;

                String[] c = splitCsvLine(line);
                if (c.length < 8) {
                    errors.add("Ligne " + row + ": colonnes insuffisantes (minimum 8 requises)");
                    skipped++;
                    continue;
                }

                try {
                    String  name         = c[0].trim();
                    String  genericName  = get(c, 1);
                    String  barcode      = get(c, 2);
                    String  category     = get(c, 3);
                    String  dosageForm   = get(c, 4);
                    String  strength     = get(c, 5);
                    boolean rxRequired   = "true".equalsIgnoreCase(get(c, 6));
                    BigDecimal unitPrice = new BigDecimal(c[7].trim());
                    int     qty          = c.length > 8  && !c[8].isBlank()  ? Integer.parseInt(c[8].trim())  : 0;
                    int     reorder      = c.length > 9  && !c[9].isBlank()  ? Integer.parseInt(c[9].trim())  : 10;
                    LocalDate expiry     = c.length > 10 && !c[10].isBlank() ? LocalDate.parse(c[10].trim())  : null;

                    if (name.isBlank()) throw new IllegalArgumentException("Le nom du médicament est vide");

                    // Find or create Medication
                    Optional<Medication> medOpt = barcode != null && !barcode.isBlank()
                        ? medicationRepo.findByBarcode(barcode)
                        : medicationRepo.findByNameIgnoreCase(name);

                    Medication med;
                    if (medOpt.isPresent()) {
                        med = medOpt.get();
                        // Update catalogue fields
                        med.setGenericName(genericName);
                        med.setCategory(category);
                        med.setDosageForm(dosageForm);
                        med.setStrength(strength);
                        med.setRequiresPrescription(rxRequired);
                        if (barcode != null && !barcode.isBlank()) med.setBarcode(barcode);
                        med = medicationRepo.save(med);
                    } else {
                        med = medicationRepo.save(Medication.builder()
                            .name(name)
                            .genericName(genericName)
                            .barcode(barcode != null && !barcode.isBlank() ? barcode : null)
                            .category(category)
                            .dosageForm(dosageForm)
                            .strength(strength)
                            .requiresPrescription(rxRequired)
                            .build());
                    }

                    // Find or create PharmacyStock
                    Optional<PharmacyStock> stockOpt =
                        stockRepo.findByPharmacyIdAndMedicationId(pharmacyId, med.getId());

                    if (stockOpt.isPresent()) {
                        PharmacyStock stock = stockOpt.get();
                        int before = stock.getQuantity();
                        stock.setQuantity(qty);
                        stock.setReorderLevel(reorder);
                        stock.setUnitPrice(unitPrice);
                        if (expiry != null) stock.setExpiryDate(expiry);
                        stock.setAvailable(qty > 0);
                        if (qty > before) stock.setLastRestocked(Instant.now());
                        stockRepo.save(stock);
                        recordMovement(stock, before, qty, ownerId,
                            StockMovement.Reason.CSV_IMPORT, "Import catalogue CSV");
                        checkAndAlert(stock);
                        updated++;
                    } else {
                        PharmacyStock stock = PharmacyStock.builder()
                            .pharmacy(pharmacy)
                            .medication(med)
                            .quantity(qty)
                            .reorderLevel(reorder)
                            .unitPrice(unitPrice)
                            .expiryDate(expiry)
                            .isAvailable(qty > 0)
                            .build();
                        stockRepo.save(stock);
                        recordMovement(stock, 0, qty, ownerId,
                            StockMovement.Reason.INITIAL_IMPORT, "Import catalogue CSV (création)");
                        checkAndAlert(stock);
                        created++;
                    }
                } catch (NumberFormatException | DateTimeParseException | IllegalArgumentException e) {
                    errors.add("Ligne " + row + ": " + e.getMessage());
                    skipped++;
                }
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException("Erreur lecture fichier CSV: " + e.getMessage());
        }

        log.info("Catalogue import pharmacy={}: created={} updated={} skipped={} errors={}",
            pharmacyId, created, updated, skipped, errors.size());
        return new StockImportResultDto(row, created, updated, skipped, errors);
    }

    // ── MOVEMENT HISTORY ──────────────────────────────────────────

    public PageResponse<StockMovementDto> stockHistory(
            UUID pharmacyId, UUID medicationId, String ownerId, Pageable pageable) {
        validateOwnership(pharmacyId, ownerId);
        PharmacyStock stock = stockRepo.findByPharmacyIdAndMedicationId(pharmacyId, medicationId)
            .orElseThrow(() -> new ResourceNotFoundException("PharmacyStock", medicationId));
        Page<StockMovement> page = movementRepo.findByStockId(stock.getId(), pageable);
        return PageResponse.of(page.map(StockMovementDto::from));
    }

    // ── Helpers ───────────────────────────────────────────────────

    private void validateOwnership(UUID pharmacyId, String ownerId) {
        Pharmacy p = pharmacyRepo.findById(pharmacyId)
            .orElseThrow(() -> new ResourceNotFoundException("Pharmacy", pharmacyId));
        if (!p.getOwner().getId().toString().equals(ownerId)) {
            throw new BusinessException("Vous n'êtes pas propriétaire de cette pharmacie");
        }
    }

    private void checkAndAlert(PharmacyStock stock) {
        if (stock.getQuantity() <= stock.getReorderLevel()) {
            notificationService.sendStockAlert(stock);
        }
    }

    private void recordMovement(PharmacyStock stock, int before, int after,
                                 String actorId, StockMovement.Reason reason, String note) {
        StockMovement m = StockMovement.builder()
            .stock(stock)
            .actorUserId(actorId)
            .quantityBefore(before)
            .quantityAfter(after)
            .delta(after - before)
            .reason(reason)
            .note(note)
            .build();
        movementRepo.save(m);
    }

    /**
     * Suggested order quantity = enough to cover 30 days of average consumption,
     * minus current stock, plus one reorder-level buffer.
     * Minimum = reorderLevel * 2 when there is no history.
     */
    private int computeSuggested(PharmacyStock stock, long consumed30d) {
        if (consumed30d <= 0) {
            return stock.getReorderLevel() * 2;
        }
        int avgDaily   = (int) Math.ceil(consumed30d / 30.0);
        int need30Days = avgDaily * 30;
        int gap        = need30Days - stock.getQuantity() + stock.getReorderLevel();
        return Math.max(stock.getReorderLevel(), gap);
    }

    /** Split a CSV line, handling simple quoted fields. */
    private String[] splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (char ch : line.toCharArray()) {
            if (ch == '"') {
                inQuotes = !inQuotes;
            } else if (ch == ',' && !inQuotes) {
                fields.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(ch);
            }
        }
        fields.add(sb.toString());
        return fields.toArray(new String[0]);
    }

    private String get(String[] arr, int i) {
        return i < arr.length ? arr[i].trim() : null;
    }
}
