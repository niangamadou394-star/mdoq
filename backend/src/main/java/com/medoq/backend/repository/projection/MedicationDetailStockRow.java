package com.medoq.backend.repository.projection;

import java.math.BigDecimal;

/**
 * Per-pharmacy stock row used in the medication detail endpoint.
 */
public interface MedicationDetailStockRow {

    String  getPharmId();
    String  getPharmName();
    String  getAddress();
    String  getCity();
    String  getRegion();
    Double  getLatitude();
    Double  getLongitude();
    String  getOpeningHoursJson();
    Boolean getIs24h();
    Double  getRating();
    Integer getReviewCount();

    Integer    getQuantity();
    BigDecimal getUnitPrice();
    Integer    getReorderLevel();
    String     getExpiryDate();     // expiry_date::text (nullable)
}
