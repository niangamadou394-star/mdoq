package com.medoq.backend.dto.search;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medoq.backend.repository.projection.MedicationSearchRow;
import com.medoq.backend.repository.projection.PharmacyNearbyRow;
import lombok.Builder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

/**
 * Pharmacy + stock entry displayed for each medication search result.
 */
@Builder
public record PharmacyStockDto(
    UUID        pharmacyId,
    String      pharmacyName,
    String      address,
    String      city,
    Double      distanceKm,        // null when no geo filter
    BigDecimal  unitPrice,
    Integer     quantity,
    StockBadge  stockBadge,
    Map<String, String> openingHours,
    boolean     is24h,
    Double      rating,
    Double      latitude,
    Double      longitude
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static PharmacyStockDto from(MedicationSearchRow row) {
        return PharmacyStockDto.builder()
                .pharmacyId(UUID.fromString(row.getPharmId()))
                .pharmacyName(row.getPharmName())
                .address(row.getAddress())
                .city(row.getCity())
                .distanceKm(round2(row.getDistanceKm()))
                .unitPrice(row.getUnitPrice())
                .quantity(row.getQuantity())
                .stockBadge(StockBadge.of(
                    orZero(row.getQuantity()),
                    orZero(row.getReorderLevel())))
                .openingHours(parseJson(row.getOpeningHoursJson()))
                .is24h(Boolean.TRUE.equals(row.getIs24h()))
                .rating(row.getRating())
                .latitude(row.getLatitude())
                .longitude(row.getLongitude())
                .build();
    }

    public static PharmacyStockDto from(PharmacyNearbyRow row) {
        return PharmacyStockDto.builder()
                .pharmacyId(UUID.fromString(row.getPharmId()))
                .pharmacyName(row.getPharmName())
                .address(row.getAddress())
                .city(row.getCity())
                .distanceKm(round2(row.getDistanceKm()))
                .unitPrice(row.getUnitPrice())
                .quantity(row.getQuantity())
                .stockBadge(row.getQuantity() != null
                    ? StockBadge.of(row.getQuantity(), orZero(row.getReorderLevel()))
                    : null)
                .openingHours(parseJson(row.getOpeningHoursJson()))
                .is24h(Boolean.TRUE.equals(row.getIs24h()))
                .rating(row.getRating())
                .latitude(row.getLatitude())
                .longitude(row.getLongitude())
                .build();
    }

    // ── helpers ───────────────────────────────────────────────────

    private static Double round2(Double v) {
        if (v == null) return null;
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static int orZero(Integer v) { return v == null ? 0 : v; }

    @SuppressWarnings("unchecked")
    static Map<String, String> parseJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
