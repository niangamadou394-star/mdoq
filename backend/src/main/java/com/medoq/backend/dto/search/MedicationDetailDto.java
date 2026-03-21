package com.medoq.backend.dto.search;

import com.medoq.backend.entity.Medication;
import com.medoq.backend.repository.projection.MedicationDetailStockRow;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Full medication detail with per-pharmacy stock info.
 */
@Builder
public record MedicationDetailDto(
    UUID    id,
    String  name,
    String  genericName,
    String  brandName,
    String  dci,
    String  category,
    String  dosageForm,
    String  strength,
    String  description,
    String  contraindications,
    boolean requiresPrescription,
    String  imageUrl,
    String  barcode,
    List<PharmacyAvailabilityDto> availability
) {
    public static MedicationDetailDto from(Medication med, List<PharmacyAvailabilityDto> availability) {
        return MedicationDetailDto.builder()
                .id(med.getId())
                .name(med.getName())
                .genericName(med.getGenericName())
                .brandName(med.getBrandName())
                .dci(med.getDci())
                .category(med.getCategory())
                .dosageForm(med.getDosageForm())
                .strength(med.getStrength())
                .description(med.getDescription())
                .contraindications(med.getContraindications())
                .requiresPrescription(med.isRequiresPrescription())
                .imageUrl(med.getImageUrl())
                .barcode(med.getBarcode())
                .availability(availability)
                .build();
    }

    @Builder
    public record PharmacyAvailabilityDto(
        UUID       pharmacyId,
        String     pharmacyName,
        String     address,
        String     city,
        String     region,
        Double     latitude,
        Double     longitude,
        Map<String, String> openingHours,
        boolean    is24h,
        Double     rating,
        Integer    reviewCount,
        BigDecimal unitPrice,
        Integer    quantity,
        StockBadge stockBadge,
        String     expiryDate
    ) {
        public static PharmacyAvailabilityDto from(MedicationDetailStockRow row) {
            return PharmacyAvailabilityDto.builder()
                    .pharmacyId(UUID.fromString(row.getPharmId()))
                    .pharmacyName(row.getPharmName())
                    .address(row.getAddress())
                    .city(row.getCity())
                    .region(row.getRegion())
                    .latitude(row.getLatitude())
                    .longitude(row.getLongitude())
                    .openingHours(PharmacyStockDto.parseJson(row.getOpeningHoursJson()))
                    .is24h(Boolean.TRUE.equals(row.getIs24h()))
                    .rating(row.getRating())
                    .reviewCount(row.getReviewCount())
                    .unitPrice(row.getUnitPrice())
                    .quantity(row.getQuantity())
                    .stockBadge(StockBadge.of(
                        row.getQuantity() != null ? row.getQuantity() : 0,
                        row.getReorderLevel() != null ? row.getReorderLevel() : 0))
                    .expiryDate(row.getExpiryDate())
                    .build();
        }
    }
}
