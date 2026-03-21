package com.medoq.backend.repository.projection;

import java.math.BigDecimal;

/**
 * Flat projection returned by the medication full-text search native queries.
 * Column aliases in SQL must use snake_case matching these getter names.
 * One row per (medication, pharmacy_stock) pair; grouped into DTOs in the service.
 */
public interface MedicationSearchRow {

    // ── Medication ────────────────────────────────────────────────
    String  getMedId();
    String  getMedName();
    String  getGenericName();
    String  getBrandName();
    String  getDci();
    String  getCategory();
    String  getDosageForm();
    String  getStrength();
    Boolean getRequiresPrescription();
    String  getImageUrl();

    // ── Pharmacy ──────────────────────────────────────────────────
    String  getPharmId();
    String  getPharmName();
    String  getAddress();
    String  getCity();
    Double  getLatitude();
    Double  getLongitude();
    String  getOpeningHoursJson();   // opening_hours::text
    Boolean getIs24h();
    Double  getRating();

    // ── Stock ─────────────────────────────────────────────────────
    Integer    getQuantity();
    BigDecimal getUnitPrice();
    Integer    getReorderLevel();

    // ── Computed ──────────────────────────────────────────────────
    Double getDistanceKm();          // null when no geo filter
}
