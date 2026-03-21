package com.medoq.backend.dto.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record InitiatePaymentRequest(

    @NotNull(message = "Reservation ID is required")
    UUID reservationId,

    /**
     * Customer phone for Orange Money USSD push (+221XXXXXXXXX).
     * Not required for Wave (customer redirected to Wave app).
     */
    @Pattern(regexp = "\\+221[0-9]{9}",
             message = "Phone must be in Senegalese format +221XXXXXXXXX")
    String customerPhone
) {}
