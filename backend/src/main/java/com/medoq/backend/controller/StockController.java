package com.medoq.backend.controller;

import com.medoq.backend.dto.admin.PageResponse;
import com.medoq.backend.dto.stock.*;
import com.medoq.backend.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Advanced stock management for pharmacy owners.
 *
 * Context path : /api/v1
 * Full prefix  : /api/v1/pharmacy/{id}/stock/**
 *
 * All endpoints require ROLE_PHARMACY_OWNER.
 * Ownership of the pharmacy is validated in StockService.
 */
@RestController
@RequestMapping("/pharmacy/{pharmacyId}/stock")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PHARMACY_OWNER')")
public class StockController {

    private final StockService stockService;

    // ── GET /pharmacy/{id}/stock?search=&page=&size= ──────────────

    /**
     * Paginated list of all stock items for the pharmacy.
     * Optional {@code search} filters by medication name or generic name.
     */
    @GetMapping
    public ResponseEntity<PageResponse<StockItemDto>> listStock(
            @PathVariable UUID   pharmacyId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal String ownerId) {

        return ResponseEntity.ok(stockService.listStock(
            pharmacyId, search, ownerId,
            PageRequest.of(page, size, Sort.by("medication.name").ascending())));
    }

    // ── PUT /pharmacy/{id}/stock/{medicationId} ───────────────────

    /**
     * Update quantity and/or reorder threshold for a single stock item.
     * Triggers STOCK_ALERT push if quantity <= reorderLevel after update.
     */
    @PutMapping("/{medicationId}")
    public ResponseEntity<StockItemDto> updateStock(
            @PathVariable UUID pharmacyId,
            @PathVariable UUID medicationId,
            @Valid @RequestBody UpdateStockRequest request,
            @AuthenticationPrincipal String ownerId) {

        return ResponseEntity.ok(
            stockService.updateStock(pharmacyId, medicationId, request, ownerId));
    }

    // ── POST /pharmacy/{id}/stock/batch ───────────────────────────

    /**
     * Bulk update stock quantities from a CSV file.
     *
     * CSV format (with header row):
     * {@code medicationId,quantity,reorderLevel}
     *
     * Returns a summary with counts of created/updated/skipped rows and any errors.
     */
    @PostMapping("/batch")
    public ResponseEntity<StockImportResultDto> batchUpdate(
            @PathVariable UUID pharmacyId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal String ownerId) {

        return ResponseEntity.ok(
            stockService.batchUpdate(pharmacyId, file, ownerId));
    }

    // ── GET /pharmacy/{id}/stock/alerts ───────────────────────────

    /**
     * Returns all stock items where {@code quantity <= reorderLevel}.
     * Each item includes a {@code suggestedOrderQty} computed from 30-day history.
     */
    @GetMapping("/alerts")
    public ResponseEntity<List<StockAlertDto>> alerts(
            @PathVariable UUID pharmacyId,
            @AuthenticationPrincipal String ownerId) {

        return ResponseEntity.ok(stockService.listAlerts(pharmacyId, ownerId));
    }

    // ── POST /pharmacy/{id}/stock/import ──────────────────────────

    /**
     * Import or update the medication catalogue from a CSV file.
     * Creates missing medications and upserts PharmacyStock entries.
     *
     * CSV format (with header row):
     * {@code name,genericName,barcode,category,dosageForm,strength,
     *         requiresPrescription,unitPrice,quantity,reorderLevel,expiryDate}
     *
     * {@code expiryDate} format: {@code yyyy-MM-dd}
     */
    @PostMapping("/import")
    public ResponseEntity<StockImportResultDto> importCatalogue(
            @PathVariable UUID pharmacyId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal String ownerId) {

        return ResponseEntity.ok(
            stockService.importCatalogue(pharmacyId, file, ownerId));
    }

    // ── GET /pharmacy/{id}/stock/{medicationId}/history ───────────

    /**
     * Paginated movement history for a specific stock item.
     * Shows who changed what quantity and when.
     */
    @GetMapping("/{medicationId}/history")
    public ResponseEntity<PageResponse<StockMovementDto>> history(
            @PathVariable UUID pharmacyId,
            @PathVariable UUID medicationId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal String ownerId) {

        return ResponseEntity.ok(stockService.stockHistory(
            pharmacyId, medicationId, ownerId,
            PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }
}
