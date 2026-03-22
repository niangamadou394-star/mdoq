package com.medoq.backend.repository;

import com.medoq.backend.entity.Pharmacy;
import com.medoq.backend.repository.projection.PharmacyNearbyRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PharmacyRepository extends JpaRepository<Pharmacy, UUID> {

    boolean existsByLicenseNumber(String licenseNumber);

    Optional<Pharmacy> findByLicenseNumber(String licenseNumber);

    // ── Admin queries ──────────────────────────────────────────────

    @Query(value = """
        SELECT p FROM Pharmacy p
        JOIN FETCH p.owner
        WHERE (:status IS NULL OR p.status = :status)
          AND (:city   IS NULL OR LOWER(p.city) LIKE LOWER(CONCAT('%', :city, '%')))
          AND (:search IS NULL
               OR LOWER(p.name)          LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(p.licenseNumber) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY p.createdAt DESC
        """,
        countQuery = """
        SELECT COUNT(p) FROM Pharmacy p
        WHERE (:status IS NULL OR p.status = :status)
          AND (:city   IS NULL OR LOWER(p.city) LIKE LOWER(CONCAT('%', :city, '%')))
          AND (:search IS NULL
               OR LOWER(p.name)          LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(p.licenseNumber) LIKE LOWER(CONCAT('%', :search, '%')))
        """)
    Page<Pharmacy> findAdminList(
        @Param("status") Pharmacy.Status status,
        @Param("city")   String city,
        @Param("search") String search,
        Pageable pageable);

    @Modifying
    @Query("UPDATE Pharmacy p SET p.status = :status WHERE p.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") Pharmacy.Status status);

    long countByStatus(Pharmacy.Status status);

    // ── All nearby active pharmacies ───────────────────────────────

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
            earth_distance(
                ll_to_earth(p.latitude::float8, p.longitude::float8),
                ll_to_earth(:lat, :lng)
            ) / 1000.0            AS distance_km,
            NULL::integer          AS quantity,
            NULL::numeric          AS unit_price,
            NULL::integer          AS reorder_level
        FROM pharmacies p
        WHERE p.status     = 'ACTIVE'
          AND p.latitude   IS NOT NULL
          AND p.longitude  IS NOT NULL
          AND earth_box(ll_to_earth(:lat, :lng), :radiusMeters)
              @> ll_to_earth(p.latitude::float8, p.longitude::float8)
        ORDER BY distance_km ASC
        LIMIT 50
        """, nativeQuery = true)
    List<PharmacyNearbyRow> findNearby(
        @Param("lat") double lat,
        @Param("lng") double lng,
        @Param("radiusMeters") double radiusMeters
    );

    // ── Nearby pharmacies that stock a specific medication ─────────

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
            earth_distance(
                ll_to_earth(p.latitude::float8, p.longitude::float8),
                ll_to_earth(:lat, :lng)
            ) / 1000.0            AS distance_km,
            ps.quantity,
            ps.unit_price,
            ps.reorder_level
        FROM pharmacies p
        JOIN pharmacy_stock ps
            ON ps.pharmacy_id   = p.id
           AND ps.medication_id = :medicationId::uuid
           AND ps.is_available  = true
           AND ps.quantity      >= 0
        WHERE p.status     = 'ACTIVE'
          AND p.latitude   IS NOT NULL
          AND p.longitude  IS NOT NULL
          AND earth_box(ll_to_earth(:lat, :lng), :radiusMeters)
              @> ll_to_earth(p.latitude::float8, p.longitude::float8)
        ORDER BY distance_km ASC
        LIMIT 50
        """, nativeQuery = true)
    List<PharmacyNearbyRow> findNearbyWithMedication(
        @Param("lat") double lat,
        @Param("lng") double lng,
        @Param("radiusMeters") double radiusMeters,
        @Param("medicationId") String medicationId
    );
}
