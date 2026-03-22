package com.medoq.backend.dto.stock;

import com.medoq.backend.entity.StockMovement;

import java.time.Instant;
import java.util.UUID;

public record StockMovementDto(
    UUID                  id,
    UUID                  stockId,
    UUID                  medicationId,
    String                medicationName,
    int                   quantityBefore,
    int                   quantityAfter,
    int                   delta,
    StockMovement.Reason  reason,
    String                actorUserId,
    String                note,
    Instant               createdAt
) {
    public static StockMovementDto from(StockMovement m) {
        return new StockMovementDto(
            m.getId(),
            m.getStock().getId(),
            m.getStock().getMedication().getId(),
            m.getStock().getMedication().getName(),
            m.getQuantityBefore(),
            m.getQuantityAfter(),
            m.getDelta(),
            m.getReason(),
            m.getActorUserId(),
            m.getNote(),
            m.getCreatedAt()
        );
    }
}
