package com.medoq.backend.repository;

import com.medoq.backend.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsByReservationId(UUID reservationId);

    @Query("""
        SELECT r FROM Review r
        JOIN FETCH r.customer
        WHERE r.pharmacy.id = :pharmacyId AND r.isVisible = true
        ORDER BY r.createdAt DESC
        """)
    Page<Review> findVisibleByPharmacyId(@Param("pharmacyId") UUID pharmacyId, Pageable pageable);

    @Query("SELECT AVG(CAST(r.rating AS double)) FROM Review r WHERE r.pharmacy.id = :pharmacyId AND r.isVisible = true")
    Double averageRatingByPharmacyId(@Param("pharmacyId") UUID pharmacyId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.pharmacy.id = :pharmacyId AND r.isVisible = true")
    long countVisibleByPharmacyId(@Param("pharmacyId") UUID pharmacyId);

    @Modifying
    @Query("UPDATE Review r SET r.isVisible = false WHERE r.id = :id")
    int hideById(@Param("id") UUID id);
}
