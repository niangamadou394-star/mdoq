package com.medoq.backend.dto.search;

import com.medoq.backend.repository.projection.MedicationSearchRow;

import java.util.List;
import java.util.UUID;

/**
 * One medication entry in the search results, with its available pharmacies.
 * Pharmacies are pre-sorted by distance (closest first).
 */
public record MedicationSearchResultDto(
    UUID   id,
    String name,
    String genericName,
    String brandName,
    String dci,
    String category,
    String dosageForm,
    String strength,
    boolean requiresPrescription,
    String  imageUrl,
    List<PharmacyStockDto> pharmacies    // sorted by distanceKm ASC
) {
    /** Build the medication part from any row of the group. */
    public static MedicationSearchResultDto of(
            MedicationSearchRow row,
            List<PharmacyStockDto> pharmacies) {
        return new MedicationSearchResultDto(
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
            pharmacies
        );
    }
}
