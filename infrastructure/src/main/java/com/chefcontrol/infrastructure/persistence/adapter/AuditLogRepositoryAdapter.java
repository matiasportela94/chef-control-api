package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.audit.AuditLog;
import com.chefcontrol.domain.repository.AuditLogRepository;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.infrastructure.persistence.PersistenceUtils;
import com.chefcontrol.infrastructure.persistence.entity.AuditLogJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AuditLogRepositoryAdapter implements AuditLogRepository {

    private final JpaAuditLogRepository jpa;

    @Override
    public AuditLog save(AuditLog entry) {
        return jpa.save(AuditLogJpaEntity.from(entry)).toDomain();
    }

    @Override
    public Page<AuditLog> findByActorIdOrderByCreatedAtDesc(UUID actorId, PageRequest pageRequest) {
        return PersistenceUtils.toDomain(
                jpa.findByActorIdOrderByCreatedAtDesc(actorId,
                        PersistenceUtils.toSpring(pageRequest, Sort.by("createdAt").descending()))
                   .map(AuditLogJpaEntity::toDomain));
    }

    @Override
    public Page<AuditLog> findByRestaurantIdOrderByCreatedAtDesc(UUID restaurantId, PageRequest pageRequest) {
        return PersistenceUtils.toDomain(
                jpa.findByRestaurantIdOrderByCreatedAtDesc(restaurantId,
                        PersistenceUtils.toSpring(pageRequest, Sort.by("createdAt").descending()))
                   .map(AuditLogJpaEntity::toDomain));
    }

    @Override
    public Page<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, UUID entityId, PageRequest pageRequest) {
        return PersistenceUtils.toDomain(
                jpa.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId,
                        PersistenceUtils.toSpring(pageRequest, Sort.by("createdAt").descending()))
                   .map(AuditLogJpaEntity::toDomain));
    }
}
