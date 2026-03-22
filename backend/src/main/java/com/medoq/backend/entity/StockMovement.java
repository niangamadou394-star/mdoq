package com.medoq.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit trail for every stock quantity change.
 * Covers manual updates, batch updates, CSV imports, and reservation adjustments.
 */
@Entity
@Table(name = "stock_movements",
       indexes = {
           @Index(name = "idx_stock_movements_stock",     columnList = "stock_id"),
           @Index(name = "idx_stock_movements_created",   columnList = "created_at"),
           @Index(name = "idx_stock_movements_actor",     columnList = "actor_user_id")
       })
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockMovement {

    public enum Reason {
        MANUAL_UPDATE,
        BATCH_UPDATE,
        CSV_IMPORT,
        INITIAL_IMPORT,
        RESERVATION_DECREMENT,
        RESERVATION_RESTORE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private PharmacyStock stock;

    /** userId string — no FK to avoid cascade issues when stock is deleted. */
    @Column(name = "actor_user_id", length = 36)
    private String actorUserId;

    @Column(name = "quantity_before", nullable = false)
    private int quantityBefore;

    @Column(name = "quantity_after", nullable = false)
    private int quantityAfter;

    /**
     * Signed delta: positive = restock, negative = consumption.
     * quantityAfter - quantityBefore.
     */
    @Column(nullable = false)
    private int delta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Reason reason;

    /** Optional free-text note (e.g. "Commande fournisseur #42"). */
    @Column(length = 255)
    private String note;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
