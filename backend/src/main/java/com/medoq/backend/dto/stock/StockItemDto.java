package com.medoq.backend.dto.stock;

import com.medoq.backend.dto.search.StockBadge;
import com.medoq.backend.entity.PharmacyStock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StockItemDto(
    UUID        stockId,
    UUID        medicationId,
    String      medicationName,
    String      genericName,
    String      category,
    String      dosageForm,
    String      strength,
    boolean     requiresPrescription,
    String      barcode,
    Integer     quantity,
    Integer     reorderLevel,
    BigDecimal  unitPrice,
    LocalDate   expiryDate,
    boolean     isAvailable,
    Instant     lastRestocked,
    StockBadge  badge
) {
    public static StockItemDto from(PharmacyStock ps) {
        var med = ps.getMedication();
        StockBadge badge = ps.getQuantity() == 0 ? StockBadge.OUT_OF_STOCK
            : ps.getQuantity() <= ps.getReorderLevel() ? StockBadge.LIMITED
            : StockBadge.AVAILABLE;
        return new StockItemDto(
            ps.getId(),
            med.getId(),
            med.getName(),
            med.getGenericName(),
            med.getCategory(),
            med.getDosageForm(),
            med.getStrength(),
            med.isRequiresPrescription(),
            med.getBarcode(),
            ps.getQuantity(),
            ps.getReorderLevel(),
            ps.getUnitPrice(),
            ps.getExpiryDate(),
            ps.isAvailable(),
            ps.getLastRestocked(),
            badge
        );
    }
}
