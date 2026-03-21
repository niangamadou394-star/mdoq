package com.medoq.backend.search;

import com.medoq.backend.dto.search.StockBadge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class StockBadgeTest {

    @ParameterizedTest(name = "qty={0}, reorder={1} → {2}")
    @CsvSource({
        "0,  10, OUT_OF_STOCK",
        "-1, 10, OUT_OF_STOCK",
        "1,  10, LIMITED",
        "10, 10, LIMITED",
        "11, 10, AVAILABLE",
        "50, 10, AVAILABLE",
        "10,  0, AVAILABLE",   // reorder_level = 0 means no threshold configured
    })
    void badgeClassification(int qty, int reorder, StockBadge expected) {
        assertThat(StockBadge.of(qty, reorder)).isEqualTo(expected);
    }

    @Test
    void zeroQuantityAlwaysOutOfStock() {
        assertThat(StockBadge.of(0, 0)).isEqualTo(StockBadge.OUT_OF_STOCK);
    }
}
