package com.medoq.backend.repository.projection;

import java.math.BigDecimal;

/**
 * Result row for the "popular medications" query.
 * Popularity = number of distinct active pharmacies that stock the medication.
 */
public interface PopularMedicationRow {

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

    Long       getPharmacyCount();  // number of pharmacies stocking it
    BigDecimal getMinPrice();       // lowest available price
}
