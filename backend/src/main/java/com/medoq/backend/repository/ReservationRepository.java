package com.medoq.backend.repository;

import com.medoq.backend.entity.Reservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    // ── Fetch joins for response mapping (avoids N+1) ─────────────

    @Query("""
        SELECT r FROM Reservation r
        JOIN FETCH r.customer
        JOIN FETCH r.pharmacy
        LEFT JOIN FETCH r.items i
        LEFT JOIN FETCH i.medication
        WHERE r.id = :id
        """)
    Optional<Reservation> findByIdWithDetails(@Param("id") UUID id);

    // ── Patient reservations ──────────────────────────────────────

    @Query(value = """
        SELECT r FROM Reservation r
        JOIN FETCH r.customer
        JOIN FETCH r.pharmacy
        WHERE r.customer.id = :customerId
        ORDER BY r.createdAt DESC
        """,
        countQuery = "SELECT COUNT(r) FROM Reservation r WHERE r.customer.id = :customerId")
    Page<Reservation> findByCustomerIdOrderByCreatedAtDesc(
        @Param("customerId") UUID customerId, Pageable pageable);

    // ── Pharmacy reservations ─────────────────────────────────────

    @Query(value = """
        SELECT r FROM Reservation r
        JOIN FETCH r.customer
        JOIN FETCH r.pharmacy
        WHERE r.pharmacy.id = :pharmacyId
        ORDER BY r.createdAt DESC
        """,
        countQuery = "SELECT COUNT(r) FROM Reservation r WHERE r.pharmacy.id = :pharmacyId")
    Page<Reservation> findByPharmacyIdOrderByCreatedAtDesc(
        @Param("pharmacyId") UUID pharmacyId, Pageable pageable);

    // ── Expiry cron queries ───────────────────────────────────────

    /** Find PENDING reservations that have passed their expiry time. */
    @Query("""
        SELECT r FROM Reservation r
        JOIN FETCH r.customer
        JOIN FETCH r.pharmacy
        LEFT JOIN FETCH r.items i
        LEFT JOIN FETCH i.medication
        WHERE r.status = 'PENDING'
          AND r.expiresAt < :now
        """)
    List<Reservation> findExpiredPending(@Param("now") Instant now);

    /**
     * Find PENDING reservations expiring between {@code from} and {@code to}
     * for which the 30-minute warning has NOT yet been sent.
     */
    @Query("""
        SELECT r FROM Reservation r
        JOIN FETCH r.customer
        JOIN FETCH r.pharmacy
        WHERE r.status = 'PENDING'
          AND r.expiryWarningSent = false
          AND r.expiresAt > :from
          AND r.expiresAt <= :to
        """)
    List<Reservation> findPendingExpiringBetween(
        @Param("from") Instant from,
        @Param("to")   Instant to);

    // ── Bulk status update ────────────────────────────────────────

    @Modifying
    @Query("""
        UPDATE Reservation r
        SET    r.status = 'EXPIRED', r.updatedAt = :now
        WHERE  r.status = 'PENDING'
          AND  r.expiresAt < :now
        """)
    int bulkExpirePending(@Param("now") Instant now);

    @Modifying
    @Query("""
        UPDATE Reservation r
        SET r.expiryWarningSent = true, r.updatedAt = :now
        WHERE r.id = :id
        """)
    int markWarningSent(@Param("id") UUID id, @Param("now") Instant now);
}
