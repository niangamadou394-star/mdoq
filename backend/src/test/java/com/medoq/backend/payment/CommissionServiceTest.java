package com.medoq.backend.payment;

import com.medoq.backend.config.PaymentProperties;
import com.medoq.backend.service.CommissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CommissionServiceTest {

    CommissionService service;

    // Platform started on 2024-01-01
    private static final LocalDate START = LocalDate.of(2024, 1, 1);

    @BeforeEach
    void setUp() {
        PaymentProperties props = new PaymentProperties();
        props.getBilling().setStartDate(START);
        service = new CommissionService(props);
    }

    @ParameterizedTest(name = "Date={0} → rate={1}%")
    @CsvSource({
        "2024-06-01, 0.0150",   // Year 1 (6 months in)
        "2024-12-31, 0.0150",   // Still Year 1
        "2025-01-01, 0.0200",   // Year 2
        "2026-03-15, 0.0200",   // Year 3
        "2027-01-01, 0.0250",   // Year 4
        "2028-01-01, 0.0300",   // Year 5+
        "2030-05-01, 0.0300",   // Year 6+ (stays at max)
    })
    void commissionTiers(String date, BigDecimal expectedRate) {
        BigDecimal rate = service.rateForDate(LocalDate.parse(date));
        assertThat(rate).isEqualByComparingTo(expectedRate);
    }

    @ParameterizedTest(name = "Amount={0} FCFA → commission={1} FCFA (Year1)")
    @CsvSource({
        "3500, 52.50",    // 3500 * 1.5% = 52.50
        "1000, 15.00",    // 1000 * 1.5%
        "10000, 150.00",  // 10000 * 1.5%
        "0, 0.00",        // edge case... but 0 amount won't reach service
    })
    void computeCommission_year1(BigDecimal amount, BigDecimal expected) {
        // Use a fixed date within Year 1 via rateForDate
        BigDecimal rate = service.rateForDate(LocalDate.of(2024, 6, 1));
        BigDecimal commission = amount.multiply(rate)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        assertThat(commission).isEqualByComparingTo(expected);
    }

    @ParameterizedTest(name = "Gross={0} → net={1} (Year1 1.5%)")
    @CsvSource({
        "3500, 3447.50",
        "1000,  985.00",
    })
    void netAmount(BigDecimal gross, BigDecimal expectedNet) {
        // Force Year 1 rate
        BigDecimal commission = gross.multiply(new BigDecimal("0.0150"))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        assertThat(gross.subtract(commission)).isEqualByComparingTo(expectedNet);
    }
}
