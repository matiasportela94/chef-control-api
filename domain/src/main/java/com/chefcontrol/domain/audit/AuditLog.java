package com.chefcontrol.domain.audit;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {

    private UUID id;
    private UUID actorId;
    private String actorEmail;
    private UUID restaurantId;
    private AuditAction action;
    private String entityType;
    private UUID entityId;
    private String payload;
    private String ipAddress;
    private Instant createdAt;
}
