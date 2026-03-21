package com.medoq.backend.service;

import com.medoq.backend.config.PaymentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;

/**
 * Calculates the Medoq platform commission based on elapsed years
 * since the platform launch date.
 *
 * Tiers:
 *   Year 1    : 1.5%
 *   Year 2-3  : 2.0%
 *   Year 4    : 2.5%
 *   Year 5+   : 3.0%
 */
@Service
@RequiredArgsConstructor
public class CommissionService {

    private static final BigDecimal RATE_Y1   = new BigDecimal("0.0150");
    private static final BigDecimal RATE_Y2_3 = new BigDecimal("0.0200");
    private static final BigDecimal RATE_Y4   = new BigDecimal("0.0250");
    private static final BigDecimal RATE_Y5   = new BigDecimal("0.0300");

    private final PaymentProperties props;

    // ── Public API ─────────────────────────────────────────────────

    /** Returns the current commission rate (e.g. 0.0150 = 1.5%). */
    public BigDecimal currentRate() {
        return rateForDate(LocalDate.now());
    }

    /**
     * Returns commission amount for a given transaction amount.
     * Rounded to nearest FCFA (no decimals for XOF).
     */
    public BigDecimal compute(BigDecimal transactionAmount) {
        BigDecimal rate = currentRate();
        return transactionAmount
                .multiply(rate)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Net amount the pharmacy receives after platform commission. */
    public BigDecimal netAmount(BigDecimal grossAmount) {
        return grossAmount.subtract(compute(grossAmount));
    }

    // ── Package-private for testing ───────────────────────────────

    BigDecimal rateForDate(LocalDate date) {
        LocalDate startDate = props.getBilling().getStartDate();
        int years = Period.between(startDate, date).getYears();

        if (years < 1) return RATE_Y1;
        if (years < 3) return RATE_Y2_3;
        if (years < 4) return RATE_Y4;
        return RATE_Y5;
    }
}
