package com.medoq.backend.dto.search;

import com.medoq.backend.repository.projection.PharmacyNearbyRow;
import lombok.Builder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

/**
 * Pharmacy entry returned by the /pharmacies/nearby endpoint.
 * {@code stock} is null when no medicationId filter is applied.
 */
@Builder
public record PharmacyNearbyDto(
    UUID    id,
    String  name,
    String  address,
    String  city,
    String  region,
    Double  latitude,
    Double  longitude,
    Map<String, String> openingHours,
    boolean is24h,
    Double  rating,
    int     reviewCount,
    Double  distanceKm,
    StockInfo stock           // null if no medication filter
) {
    public record StockInfo(
        Integer    quantity,
        BigDecimal unitPrice,
        StockBadge stockBadge
    ) {}

    public static PharmacyNearbyDto from(PharmacyNearbyRow row) {
        StockInfo stock = null;
        if (row.getQuantity() != null) {
            stock = new StockInfo(
                row.getQuantity(),
                row.getUnitPrice(),
                StockBadge.of(row.getQuantity(),
                    row.getReorderLevel() != null ? row.getReorderLevel() : 0));
        }

        return PharmacyNearbyDto.builder()
                .id(UUID.fromString(row.getPharmId()))
                .name(row.getPharmName())
                .address(row.getAddress())
                .city(row.getCity())
                .region(row.getRegion())
                .latitude(row.getLatitude())
                .longitude(row.getLongitude())
                .openingHours(PharmacyStockDto.parseJson(row.getOpeningHoursJson()))
                .is24h(Boolean.TRUE.equals(row.getIs24h()))
                .rating(row.getRating())
                .reviewCount(row.getReviewCount() != null ? row.getReviewCount() : 0)
                .distanceKm(round2(row.getDistanceKm()))
                .stock(stock)
                .build();
    }

    private static Double round2(Double v) {
        if (v == null) return null;
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
