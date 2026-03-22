package com.medoq.backend.repository;

import com.medoq.backend.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByResourceTypeAndResourceIdOrderByTimestampDesc(
        String resourceType, UUID resourceId, Pageable pageable);

    Page<AuditLog> findByUserIdOrderByTimestampDesc(UUID userId, Pageable pageable);

    // ── Admin queries ──────────────────────────────────────────────

    @Query(value = """
        SELECT a FROM AuditLog a
        LEFT JOIN FETCH a.user
        WHERE (:action       IS NULL OR a.action       = :action)
          AND (:resourceType IS NULL OR a.resourceType = :resourceType)
          AND (:status       IS NULL OR a.status       = :status)
          AND (:from         IS NULL OR a.timestamp   >= :from)
          AND (:to           IS NULL OR a.timestamp   <= :to)
        ORDER BY a.timestamp DESC
        """,
        countQuery = """
        SELECT COUNT(a) FROM AuditLog a
        WHERE (:action       IS NULL OR a.action       = :action)
          AND (:resourceType IS NULL OR a.resourceType = :resourceType)
          AND (:status       IS NULL OR a.status       = :status)
          AND (:from         IS NULL OR a.timestamp   >= :from)
          AND (:to           IS NULL OR a.timestamp   <= :to)
        """)
    Page<AuditLog> findAdminList(
        @Param("action")       String         action,
        @Param("resourceType") String         resourceType,
        @Param("status")       AuditLog.Status status,
        @Param("from")         Instant         from,
        @Param("to")           Instant         to,
        Pageable pageable);
}
