package com.medoq.backend.dto.stock;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateStockRequest(

    @NotNull(message = "La quantité est obligatoire")
    @Min(value = 0, message = "La quantité ne peut pas être négative")
    Integer quantity,

    @Min(value = 0, message = "Le seuil d'alerte ne peut pas être négatif")
    Integer reorderLevel,

    @DecimalMin(value = "0.0", inclusive = false, message = "Le prix doit être positif")
    BigDecimal unitPrice,

    LocalDate expiryDate,

    @Size(max = 255)
    String note
) {}
