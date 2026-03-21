package com.medoq.backend.service;

import com.medoq.backend.entity.AuditLog;
import com.medoq.backend.entity.User;
import com.medoq.backend.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Persists immutable audit log entries for all payment and sensitive operations.
 * Runs asynchronously in a new transaction so that a main transaction rollback
 * does NOT prevent the failure audit entry from being persisted.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    // ── Payment audit actions ─────────────────────────────────────

    public static final String ACTION_PAYMENT_INITIATED  = "PAYMENT_INITIATED";
    public static final String ACTION_PAYMENT_COMPLETED  = "PAYMENT_COMPLETED";
    public static final String ACTION_PAYMENT_FAILED     = "PAYMENT_FAILED";
    public static final String ACTION_PAYMENT_REFUNDED   = "PAYMENT_REFUNDED";
    public static final String ACTION_WEBHOOK_RECEIVED   = "WEBHOOK_RECEIVED";
    public static final String ACTION_INVOICE_GENERATED  = "INVOICE_GENERATED";
    public static final String ACTION_INVOICE_SENT       = "INVOICE_SENT";

    // ── Log methods ───────────────────────────────────────────────

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSuccess(String action, String resourceType, UUID resourceId,
                           User actor, String ipAddress, Map<String, Object> metadata) {
        persist(action, resourceType, resourceId, actor, ipAddress,
            AuditLog.Status.SUCCESS, null, null, metadata);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(String action, String resourceType, UUID resourceId,
                           User actor, String ipAddress,
                           Map<String, Object> metadata, String reason) {
        Map<String, Object> meta = metadata != null
            ? new java.util.HashMap<>(metadata)
            : new java.util.HashMap<>();
        meta.put("failureReason", reason);

        persist(action, resourceType, resourceId, actor, ipAddress,
            AuditLog.Status.FAILURE, null, null, meta);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logStateChange(String action, String resourceType, UUID resourceId,
                                User actor, String ipAddress,
                                Map<String, Object> oldValue, Map<String, Object> newValue) {
        persist(action, resourceType, resourceId, actor, ipAddress,
            AuditLog.Status.SUCCESS, oldValue, newValue, null);
    }

    // ── Internal ──────────────────────────────────────────────────

    private void persist(String action, String resourceType, UUID resourceId,
                          User actor, String ipAddress, AuditLog.Status status,
                          Map<String, Object> oldValue, Map<String, Object> newValue,
                          Map<String, Object> metadata) {
        try {
            AuditLog entry = AuditLog.builder()
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .user(actor)
                    .ipAddress(ipAddress)
                    .status(status)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .metadata(metadata)
                    .build();
            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Never let audit failure break the main flow
            log.error("Failed to persist audit log [{}] {}/{}: {}",
                action, resourceType, resourceId, e.getMessage());
        }
    }
}
