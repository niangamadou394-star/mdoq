package com.medoq.backend.dto.admin;

import com.medoq.backend.entity.Pharmacy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminPharmacyDto(
        UUID           id,
        String         name,
        String         licenseNumber,
        String         city,
        String         region,
        String         phone,
        String         email,
        Pharmacy.Status status,
        BigDecimal     rating,
        int            reviewCount,
        String         ownerName,
        String         ownerPhone,
        Instant        createdAt
) {
    public static AdminPharmacyDto from(Pharmacy p) {
        String owner = p.getOwner() != null
            ? p.getOwner().getFirstName() + " " + p.getOwner().getLastName()
            : null;
        String ownerPhone = p.getOwner() != null ? p.getOwner().getPhone() : null;
        return new AdminPharmacyDto(
            p.getId(), p.getName(), p.getLicenseNumber(),
            p.getCity(), p.getRegion(), p.getPhone(), p.getEmail(),
            p.getStatus(), p.getRating(), p.getReviewCount(),
            owner, ownerPhone, p.getCreatedAt()
        );
    }
}
