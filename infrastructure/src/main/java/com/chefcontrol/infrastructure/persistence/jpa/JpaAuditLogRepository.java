package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.infrastructure.persistence.entity.AuditLogJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface JpaAuditLogRepository extends JpaRepository<AuditLogJpaEntity, UUID> {

    Page<AuditLogJpaEntity> findByActorIdOrderByCreatedAtDesc(UUID actorId, Pageable pageable);

    Page<AuditLogJpaEntity> findByRestaurantIdOrderByCreatedAtDesc(UUID restaurantId, Pageable pageable);

    Page<AuditLogJpaEntity> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, UUID entityId, Pageable pageable);

    @Query("""
            SELECT a FROM AuditLogJpaEntity a
            WHERE a.restaurantId = :restaurantId
              AND (:actorEmail IS NULL OR a.actorEmail = :actorEmail)
              AND (:action IS NULL OR a.action = :action)
              AND (:entityType IS NULL OR a.entityType = :entityType)
              AND (:from IS NULL OR a.createdAt >= :from)
              AND (:to IS NULL OR a.createdAt <= :to)
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLogJpaEntity> search(@Param("restaurantId") UUID restaurantId,
                                   @Param("actorEmail") String actorEmail,
                                   @Param("action") AuditAction action,
                                   @Param("entityType") String entityType,
                                   @Param("from") Instant from,
                                   @Param("to") Instant to,
                                   Pageable pageable);
}
