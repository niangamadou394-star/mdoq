package com.medoq.backend.dto.admin;

import com.medoq.backend.entity.AuditLog;

import java.time.Instant;
import java.util.UUID;

public record AuditLogDto(
        UUID            id,
        Instant         timestamp,
        String          action,
        String          resourceType,
        UUID            resourceId,
        String          ipAddress,
        AuditLog.Status status,
        String          actorName,
        String          actorPhone
) {
    public static AuditLogDto from(AuditLog a) {
        String name  = null;
        String phone = null;
        if (a.getUser() != null) {
            name  = a.getUser().getFirstName() + " " + a.getUser().getLastName();
            phone = a.getUser().getPhone();
        }
        return new AuditLogDto(
            a.getId(), a.getTimestamp(), a.getAction(),
            a.getResourceType(), a.getResourceId(),
            a.getIpAddress(), a.getStatus(),
            name, phone
        );
    }
}
