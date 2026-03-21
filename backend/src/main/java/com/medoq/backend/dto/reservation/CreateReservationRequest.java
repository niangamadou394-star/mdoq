package com.medoq.backend.dto.reservation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateReservationRequest(

    @NotNull(message = "Pharmacy ID is required")
    UUID pharmacyId,

    @NotEmpty(message = "At least one item is required")
    @Size(max = 20, message = "Cannot order more than 20 different medications at once")
    @Valid
    List<ReservationItemRequest> items,

    /** Optional desired pickup time. Defaults to 2h from now if null. */
    Instant pickupDate,

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    String notes
) {}
