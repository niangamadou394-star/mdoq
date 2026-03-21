package com.medoq.backend.dto.search;

import com.medoq.backend.repository.projection.PopularMedicationRow;

import java.math.BigDecimal;
import java.util.UUID;

public record PopularMedicationDto(
    UUID       id,
    String     name,
    String     genericName,
    String     brandName,
    String     dci,
    String     category,
    String     dosageForm,
    String     strength,
    boolean    requiresPrescription,
    String     imageUrl,
    long       pharmacyCount,
    BigDecimal minPrice
) {
    public static PopularMedicationDto from(PopularMedicationRow row) {
        return new PopularMedicationDto(
            UUID.fromString(row.getMedId()),
            row.getMedName(),
            row.getGenericName(),
            row.getBrandName(),
            row.getDci(),
            row.getCategory(),
            row.getDosageForm(),
            row.getStrength(),
            Boolean.TRUE.equals(row.getRequiresPrescription()),
            row.getImageUrl(),
            row.getPharmacyCount() != null ? row.getPharmacyCount() : 0L,
            row.getMinPrice()
        );
    }
}
