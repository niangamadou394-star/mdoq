package com.medoq.backend.repository.projection;

import java.math.BigDecimal;

/**
 * Flat projection for the nearby-pharmacies query.
 * Stock fields are null when the query is run without a medication filter.
 */
public interface PharmacyNearbyRow {

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
    Double  getDistanceKm();

    // nullable — only populated when a medicationId is provided
    Integer    getQuantity();
    BigDecimal getUnitPrice();
    Integer    getReorderLevel();
}
