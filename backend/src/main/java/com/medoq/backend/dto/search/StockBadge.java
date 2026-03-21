package com.medoq.backend.dto.search;

/**
 * Stock availability badge shown to the customer.
 *
 * AVAILABLE    : quantity > reorder_level
 * LIMITED      : 0 < quantity <= reorder_level  (below safety threshold)
 * OUT_OF_STOCK : quantity = 0
 */
public enum StockBadge {
    AVAILABLE,
    LIMITED,
    OUT_OF_STOCK;

    public static StockBadge of(int quantity, int reorderLevel) {
        if (quantity <= 0)           return OUT_OF_STOCK;
        if (quantity <= reorderLevel) return LIMITED;
        return AVAILABLE;
    }
}
