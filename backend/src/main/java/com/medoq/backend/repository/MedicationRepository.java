package com.medoq.backend.repository;

import com.medoq.backend.entity.Medication;
import com.medoq.backend.repository.projection.MedicationDetailStockRow;
import com.medoq.backend.repository.projection.MedicationSearchRow;
import com.medoq.backend.repository.projection.PopularMedicationRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicationRepository extends JpaRepository<Medication, UUID> {

    // ── Full-text search WITHOUT geo ───────────────────────────────

    @Query(value = """
        SELECT
            m.id::text            AS med_id,
            m.name                AS med_name,
            m.generic_name,
            m.brand_name,
            m.dci,
            m.category,
            m.dosage_form,
            m.strength,
            m.requires_prescription,
            m.image_url,
            p.id::text            AS pharm_id,
            p.name                AS pharm_name,
            p.address,
            p.city,
            p.latitude,
            p.longitude,
            p.opening_hours::text AS opening_hours_json,
            p.is_24h,
            p.rating,
            ps.quantity,
            ps.unit_price,
            ps.reorder_level,
            NULL::double precision AS distance_km
        FROM medications m
        JOIN pharmacy_stock ps
            ON ps.medication_id = m.id
           AND ps.is_available  = true
           AND ps.quantity      >= 0
        JOIN pharmacies p
            ON p.id     = ps.pharmacy_id
           AND p.status = 'ACTIVE'
        WHERE (
            to_tsvector('french',
                m.name                              || ' ' ||
                COALESCE(m.generic_name,  '')       || ' ' ||
                COALESCE(m.dci,           '')       || ' ' ||
                COALESCE(m.brand_name,    '')       || ' ' ||
                COALESCE(m.category,      '')
            ) @@ plainto_tsquery('french', :query)
            OR m.name          ILIKE '%' || :query || '%'
            OR m.generic_name  ILIKE '%' || :query || '%'
            OR m.dci           ILIKE '%' || :query || '%'
            OR m.brand_name    ILIKE '%' || :query || '%'
            OR m.category      ILIKE '%' || :query || '%'
        )
        ORDER BY m.name ASC
        LIMIT :maxMedications
        """, nativeQuery = true)
    List<MedicationSearchRow> searchWithoutGeo(
        @Param("query") String query,
        @Param("maxMedications") int maxMedications
    );

    // ── Full-text search WITH geo (radius in metres) ───────────────

    @Query(value = """
        SELECT
            m.id::text            AS med_id,
            m.name                AS med_name,
            m.generic_name,
            m.brand_name,
            m.dci,
            m.category,
            m.dosage_form,
            m.strength,
            m.requires_prescription,
            m.image_url,
            p.id::text            AS pharm_id,
            p.name                AS pharm_name,
            p.address,
            p.city,
            p.latitude,
            p.longitude,
            p.opening_hours::text AS opening_hours_json,
            p.is_24h,
            p.rating,
            ps.quantity,
            ps.unit_price,
            ps.reorder_level,
            earth_distance(
                ll_to_earth(p.latitude::float8, p.longitude::float8),
                ll_to_earth(:lat,               :lng)
            ) / 1000.0            AS distance_km
        FROM medications m
        JOIN pharmacy_stock ps
            ON ps.medication_id = m.id
           AND ps.is_available  = true
           AND ps.quantity      >= 0
        JOIN pharmacies p
            ON p.id        = ps.pharmacy_id
           AND p.status    = 'ACTIVE'
           AND p.latitude  IS NOT NULL
           AND p.longitude IS NOT NULL
        WHERE (
            to_tsvector('french',
                m.name                              || ' ' ||
                COALESCE(m.generic_name,  '')       || ' ' ||
                COALESCE(m.dci,           '')       || ' ' ||
                COALESCE(m.brand_name,    '')       || ' ' ||
                COALESCE(m.category,      '')
            ) @@ plainto_tsquery('french', :query)
            OR m.name          ILIKE '%' || :query || '%'
            OR m.generic_name  ILIKE '%' || :query || '%'
            OR m.dci           ILIKE '%' || :query || '%'
            OR m.brand_name    ILIKE '%' || :query || '%'
            OR m.category      ILIKE '%' || :query || '%'
        )
        AND earth_box(ll_to_earth(:lat, :lng), :radiusMeters)
            @> ll_to_earth(p.latitude::float8, p.longitude::float8)
        ORDER BY distance_km ASC, m.name ASC
        LIMIT :maxMedications
        """, nativeQuery = true)
    List<MedicationSearchRow> searchWithGeo(
        @Param("query") String query,
        @Param("lat") double lat,
        @Param("lng") double lng,
        @Param("radiusMeters") double radiusMeters,
        @Param("maxMedications") int maxMedications
    );

    // ── Popular medications ────────────────────────────────────────

    @Query(value = """
        SELECT
            m.id::text            AS med_id,
            m.name                AS med_name,
            m.generic_name,
            m.brand_name,
            m.dci,
            m.category,
            m.dosage_form,
            m.strength,
            m.requires_prescription,
            m.image_url,
            COUNT(DISTINCT ps.pharmacy_id) AS pharmacy_count,
            MIN(ps.unit_price)             AS min_price
        FROM medications m
        JOIN pharmacy_stock ps
            ON ps.medication_id = m.id
           AND ps.is_available  = true
           AND ps.quantity      > 0
        JOIN pharmacies p
            ON p.id     = ps.pharmacy_id
           AND p.status = 'ACTIVE'
        GROUP BY m.id
        ORDER BY pharmacy_count DESC, m.name ASC
        LIMIT 20
        """, nativeQuery = true)
    List<PopularMedicationRow> findPopular();

    // ── Medication detail + per-pharmacy stock ─────────────────────

    @Query(value = """
        SELECT
            p.id::text            AS pharm_id,
            p.name                AS pharm_name,
            p.address,
            p.city,
            p.region,
            p.latitude,
            p.longitude,
            p.opening_hours::text AS opening_hours_json,
            p.is_24h,
            p.rating,
            p.review_count,
            ps.quantity,
            ps.unit_price,
            ps.reorder_level,
            ps.expiry_date::text  AS expiry_date
        FROM pharmacy_stock ps
        JOIN pharmacies p
            ON p.id     = ps.pharmacy_id
           AND p.status = 'ACTIVE'
        WHERE ps.medication_id = :medId::uuid
          AND ps.is_available  = true
        ORDER BY ps.unit_price ASC
        """, nativeQuery = true)
    List<MedicationDetailStockRow> findDetailStockByMedicationId(
        @Param("medId") String medId
    );

    // ── Used by StockService CSV import ──────────────────────────

    Optional<Medication> findByBarcode(String barcode);

    Optional<Medication> findByNameIgnoreCase(String name);
}
