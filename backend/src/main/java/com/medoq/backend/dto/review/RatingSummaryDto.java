package com.medoq.backend.dto.review;

import java.math.BigDecimal;
import java.util.UUID;

public record RatingSummaryDto(
    UUID       pharmacyId,
    String     pharmacyName,
    BigDecimal averageRating,
    long       reviewCount
) {}
