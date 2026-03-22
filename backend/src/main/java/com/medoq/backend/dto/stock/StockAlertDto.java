package com.medoq.backend.dto.stock;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Represents one stock item that has dropped to or below its reorder threshold.
 * {@code suggestedOrderQty} is computed from 30-day consumption history.
 */
public record StockAlertDto(
    UUID       stockId,
    UUID       medicationId,
    String     medicationName,
    String     genericName,
    Integer    currentQuantity,
    Integer    reorderLevel,
    BigDecimal unitPrice,
    /** Recommended quantity to order to cover ~30 days + safety stock. */
    int        suggestedOrderQty
) {}
