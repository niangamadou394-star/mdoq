package com.medoq.backend.repository;

import com.medoq.backend.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    @Query("""
        SELECT sm FROM StockMovement sm
        WHERE sm.stock.id = :stockId
        ORDER BY sm.createdAt DESC
        """)
    Page<StockMovement> findByStockId(@Param("stockId") UUID stockId, Pageable pageable);

    /**
     * Sum of absolute consumption (negative deltas) since a given instant.
     * Used to compute 30-day consumption for order suggestions.
     */
    @Query("""
        SELECT COALESCE(SUM(ABS(sm.delta)), 0)
        FROM StockMovement sm
        WHERE sm.stock.id = :stockId
          AND sm.delta < 0
          AND sm.createdAt >= :since
        """)
    Long totalConsumedSince(@Param("stockId") UUID stockId, @Param("since") Instant since);
}
