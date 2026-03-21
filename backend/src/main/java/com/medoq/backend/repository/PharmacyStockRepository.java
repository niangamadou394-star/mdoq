package com.medoq.backend.repository;

import com.medoq.backend.entity.PharmacyStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PharmacyStockRepository extends JpaRepository<PharmacyStock, UUID> {

    Optional<PharmacyStock> findByPharmacyIdAndMedicationId(
        UUID pharmacyId, UUID medicationId);

    /**
     * Acquires a row-level write lock on the stock entry.
     * Used during reservation confirmation to prevent concurrent over-decrement.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT ps FROM PharmacyStock ps
        WHERE ps.pharmacy.id  = :pharmacyId
          AND ps.medication.id = :medicationId
        """)
    Optional<PharmacyStock> findForUpdate(
        @Param("pharmacyId")   UUID pharmacyId,
        @Param("medicationId") UUID medicationId);

    /** Atomically decrement quantity — safety check prevents going below zero. */
    @Modifying
    @Query("""
        UPDATE PharmacyStock ps
        SET ps.quantity   = ps.quantity - :qty,
            ps.isAvailable = CASE WHEN (ps.quantity - :qty) > 0 THEN true ELSE false END,
            ps.updatedAt  = CURRENT_TIMESTAMP
        WHERE ps.pharmacy.id   = :pharmacyId
          AND ps.medication.id = :medicationId
          AND ps.quantity      >= :qty
        """)
    int decrementStock(
        @Param("pharmacyId")   UUID pharmacyId,
        @Param("medicationId") UUID medicationId,
        @Param("qty")          int qty);

    /** Restore stock when a confirmed reservation is cancelled. */
    @Modifying
    @Query("""
        UPDATE PharmacyStock ps
        SET ps.quantity    = ps.quantity + :qty,
            ps.isAvailable = true,
            ps.updatedAt   = CURRENT_TIMESTAMP
        WHERE ps.pharmacy.id   = :pharmacyId
          AND ps.medication.id = :medicationId
        """)
    int incrementStock(
        @Param("pharmacyId")   UUID pharmacyId,
        @Param("medicationId") UUID medicationId,
        @Param("qty")          int qty);
}
