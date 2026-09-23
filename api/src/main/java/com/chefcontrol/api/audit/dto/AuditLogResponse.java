package com.chefcontrol.api.audit.dto;

import com.chefcontrol.domain.audit.AuditLog;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID actorId,
        String actorEmail,
        String action,
        String entityType,
        UUID entityId,
        String payload,
        String ipAddress,
        Instant createdAt
) {
    public static AuditLogResponse from(AuditLog a) {
        return new AuditLogResponse(
                a.getId(), a.getActorId(), a.getActorEmail(),
                a.getAction().name(), a.getEntityType(), a.getEntityId(),
                a.getPayload(), a.getIpAddress(), a.getCreatedAt());
    }
}
