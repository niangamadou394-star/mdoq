package com.medoq.backend.dto.reservation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReservationItemRequest(

    @NotNull(message = "Medication ID is required")
    UUID medicationId,

    @NotNull(message = "Quantity is required")
    @Min(value = 1,   message = "Quantity must be at least 1")
    @Max(value = 999, message = "Quantity cannot exceed 999")
    Integer quantity
) {}
