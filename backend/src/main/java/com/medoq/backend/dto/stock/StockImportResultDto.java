package com.medoq.backend.dto.stock;

import java.util.List;

/** Summary returned after a batch update or catalog import from CSV. */
public record StockImportResultDto(
    int          processedRows,
    int          created,
    int          updated,
    int          skipped,
    List<String> errors
) {
    public boolean hasErrors() { return !errors.isEmpty(); }
}
