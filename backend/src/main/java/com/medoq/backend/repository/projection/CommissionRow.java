package com.medoq.backend.repository.projection;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Projection interface for the commission GROUP-BY query.
 */
public interface CommissionRow {
    UUID       getPharmacyId();
    String     getPharmacyName();
    Long       getTransactionCount();
    BigDecimal getGrossRevenue();
    BigDecimal getCommissionAmount();
    BigDecimal getNetAmount();
}
