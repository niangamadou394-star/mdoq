package com.medoq.backend.dto.review;

import com.medoq.backend.entity.Review;

import java.time.Instant;
import java.util.UUID;

public record ReviewDto(
    UUID    id,
    UUID    reservationId,
    UUID    pharmacyId,
    String  customerFirstName,
    String  customerLastName,
    short   rating,
    String  comment,
    boolean isVerified,
    Instant createdAt
) {
    public static ReviewDto from(Review r) {
        return new ReviewDto(
            r.getId(),
            r.getReservation().getId(),
            r.getPharmacy().getId(),
            r.getCustomer().getFirstName(),
            r.getCustomer().getLastName(),
            r.getRating(),
            r.getComment(),
            r.isVerified(),
            r.getCreatedAt()
        );
    }
}
