package com.medoq.backend.dto.review;

import jakarta.validation.constraints.*;

import java.util.UUID;

public record CreateReviewRequest(

    @NotNull(message = "L'identifiant de la réservation est obligatoire")
    UUID reservationId,

    @NotNull(message = "La note est obligatoire")
    @Min(value = 1, message = "La note minimale est 1")
    @Max(value = 5, message = "La note maximale est 5")
    Short rating,

    @Size(max = 500, message = "Le commentaire ne peut pas dépasser 500 caractères")
    String comment
) {}
