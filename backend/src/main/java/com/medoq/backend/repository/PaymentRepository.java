package com.medoq.backend.repository;

import com.medoq.backend.entity.Payment;
import com.medoq.backend.repository.projection.CommissionRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @Query("""
        SELECT p FROM Payment p
        JOIN FETCH p.reservation r
        JOIN FETCH r.customer
        JOIN FETCH r.pharmacy
        LEFT JOIN FETCH r.items i
        LEFT JOIN FETCH i.medication
        WHERE p.id = :id
        """)
    Optional<Payment> findByIdWithDetails(@Param("id") UUID id);

    Optional<Payment> findByTransactionRef(String transactionRef);

    Optional<Payment> findByProviderSession(String providerSession);

    List<Payment> findByReservationIdOrderByCreatedAtDesc(UUID reservationId);

    /** Idempotency check: is this event already processed? */
    boolean existsByTransactionRef(String transactionRef);

    // ── Admin queries ──────────────────────────────────────────────

    @Query(value = """
        SELECT p FROM Payment p
        JOIN FETCH p.reservation r
        JOIN FETCH r.customer
        JOIN FETCH r.pharmacy
        WHERE (:status IS NULL OR p.status = :status)
          AND (:method IS NULL OR p.method = :method)
          AND (:from   IS NULL OR p.createdAt >= :from)
          AND (:to     IS NULL OR p.createdAt <= :to)
        ORDER BY p.createdAt DESC
        """,
        countQuery = """
        SELECT COUNT(p) FROM Payment p
        WHERE (:status IS NULL OR p.status = :status)
          AND (:method IS NULL OR p.method = :method)
          AND (:from   IS NULL OR p.createdAt >= :from)
          AND (:to     IS NULL OR p.createdAt <= :to)
        """)
    Page<Payment> findAdminTransactions(
        @Param("status") Payment.Status status,
        @Param("method") Payment.Method method,
        @Param("from")   Instant        from,
        @Param("to")     Instant        to,
        Pageable pageable);

    /** Per-pharmacy commission breakdown for a date range. */
    @Query(value = """
        SELECT
            r.pharmacy_id    AS pharmacyId,
            ph.name          AS pharmacyName,
            COUNT(p.id)      AS transactionCount,
            COALESCE(SUM(p.amount),            0) AS grossRevenue,
            COALESCE(SUM(p.commission_amount), 0) AS commissionAmount,
            COALESCE(SUM(p.net_amount),        0) AS netAmount
        FROM payments p
        JOIN reservations r  ON r.id  = p.reservation_id
        JOIN pharmacies   ph ON ph.id = r.pharmacy_id
        WHERE p.status = 'COMPLETED'
          AND (:from IS NULL OR p.created_at >= :from)
          AND (:to   IS NULL OR p.created_at <= :to)
        GROUP BY r.pharmacy_id, ph.name
        ORDER BY commissionAmount DESC
        """, nativeQuery = true)
    List<CommissionRow> commissionByPharmacy(
        @Param("from") Instant from,
        @Param("to")   Instant to);

    /** Global revenue aggregates for a date range. */
    @Query("""
        SELECT COALESCE(SUM(p.amount),           0),
               COALESCE(SUM(p.commissionAmount), 0),
               COUNT(p)
        FROM Payment p
        WHERE p.status = com.medoq.backend.entity.Payment.Status.COMPLETED
          AND (:from IS NULL OR p.createdAt >= :from)
          AND (:to   IS NULL OR p.createdAt <= :to)
        """)
    Object[] revenueSummary(@Param("from") Instant from, @Param("to") Instant to);

    /** Pharmacy-level stats for the admin pharmacy stats endpoint. */
    @Query(value = """
        SELECT
            COUNT(r.id)                                                           AS totalReservations,
            COUNT(r.id) FILTER (WHERE r.status = 'COMPLETED')                    AS completedReservations,
            COUNT(r.id) FILTER (WHERE r.status = 'CANCELLED')                    AS cancelledReservations,
            COUNT(r.id) FILTER (WHERE r.status = 'EXPIRED')                      AS expiredReservations,
            COALESCE(SUM(p.amount)            FILTER (WHERE p.status='COMPLETED'), 0) AS totalRevenue,
            COALESCE(SUM(p.commission_amount) FILTER (WHERE p.status='COMPLETED'), 0) AS totalCommission,
            COALESCE(SUM(p.net_amount)        FILTER (WHERE p.status='COMPLETED'), 0) AS netRevenue
        FROM reservations r
        LEFT JOIN payments p ON p.reservation_id = r.id
        WHERE r.pharmacy_id = :pharmacyId
        """, nativeQuery = true)
    com.medoq.backend.repository.projection.PharmacyStatsRow pharmacyStats(
        @Param("pharmacyId") UUID pharmacyId);

    // ── Dashboard aggregates ──────────────────────────────────────

    @Query("""
        SELECT COALESCE(SUM(p.amount),           0),
               COALESCE(SUM(p.commissionAmount), 0)
        FROM Payment p
        WHERE p.status = com.medoq.backend.entity.Payment.Status.COMPLETED
          AND p.paidAt >= :from
        """)
    Object[] revenueFrom(@Param("from") Instant from);

    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.createdAt >= :from")
    long countReservationsFrom(@Param("from") Instant from);

    /** Total gross revenue for a single pharmacy (all time, COMPLETED). */
    @Query("""
        SELECT COALESCE(SUM(p.amount), 0) FROM Payment p
        JOIN p.reservation r
        WHERE r.pharmacy.id = :pharmacyId
          AND p.status = com.medoq.backend.entity.Payment.Status.COMPLETED
        """)
    BigDecimal totalRevenueByPharmacy(@Param("pharmacyId") UUID pharmacyId);
}
