package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.audit.AuditLog;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
@Getter @Setter @NoArgsConstructor
public class AuditLogJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_email")
    private String actorEmail;

    @Column(name = "restaurant_id")
    private UUID restaurantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public static AuditLogJpaEntity from(AuditLog domain) {
        AuditLogJpaEntity e = new AuditLogJpaEntity();
        e.setId(domain.getId());
        e.setActorId(domain.getActorId());
        e.setActorEmail(domain.getActorEmail());
        e.setRestaurantId(domain.getRestaurantId());
        e.setAction(domain.getAction());
        e.setEntityType(domain.getEntityType());
        e.setEntityId(domain.getEntityId());
        e.setPayload(domain.getPayload());
        e.setIpAddress(domain.getIpAddress());
        e.setCreatedAt(domain.getCreatedAt());
        return e;
    }

    public AuditLog toDomain() {
        return AuditLog.builder()
                .id(id)
                .actorId(actorId)
                .actorEmail(actorEmail)
                .restaurantId(restaurantId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .payload(payload)
                .ipAddress(ipAddress)
                .createdAt(createdAt)
                .build();
    }
}
