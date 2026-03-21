package com.medoq.backend.dto.reservation;

import com.medoq.backend.entity.Reservation;
import com.medoq.backend.entity.ReservationItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full reservation payload returned for all endpoints.
 */
public record ReservationResponse(

    UUID               id,
    String             reference,
    Reservation.Status status,
    BigDecimal         totalAmount,
    Instant            expiresAt,
    Instant            pickupDate,
    String             notes,

    CustomerInfo       customer,
    PharmacyInfo       pharmacy,
    List<ItemInfo>     items,

    Instant confirmedAt,
    Instant completedAt,
    Instant cancelledAt,
    String  cancellationReason,

    Instant createdAt,
    Instant updatedAt

) {
    // ── Nested records ────────────────────────────────────────────

    public record CustomerInfo(UUID id, String firstName, String lastName, String phone) {}

    public record PharmacyInfo(UUID id, String name, String address, String city, String phone) {}

    public record ItemInfo(
        UUID       medicationId,
        String     medicationName,
        String     medicationStrength,
        Integer    quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
    ) {}

    // ── Factory ───────────────────────────────────────────────────

    public static ReservationResponse from(Reservation r) {
        var customer = new CustomerInfo(
            r.getCustomer().getId(),
            r.getCustomer().getFirstName(),
            r.getCustomer().getLastName(),
            r.getCustomer().getPhone());

        var pharmacy = new PharmacyInfo(
            r.getPharmacy().getId(),
            r.getPharmacy().getName(),
            r.getPharmacy().getAddress(),
            r.getPharmacy().getCity(),
            r.getPharmacy().getPhone());

        var items = r.getItems().stream()
                .map(item -> new ItemInfo(
                    item.getMedication().getId(),
                    item.getMedication().getName(),
                    item.getMedication().getStrength(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))))
                .toList();

        return new ReservationResponse(
            r.getId(),
            r.getReference(),
            r.getStatus(),
            r.getTotalAmount(),
            r.getExpiresAt(),
            r.getPickupDate(),
            r.getNotes(),
            customer,
            pharmacy,
            items,
            r.getConfirmedAt(),
            r.getCompletedAt(),
            r.getCancelledAt(),
            r.getCancellationReason(),
            r.getCreatedAt(),
            r.getUpdatedAt()
        );
    }
}
