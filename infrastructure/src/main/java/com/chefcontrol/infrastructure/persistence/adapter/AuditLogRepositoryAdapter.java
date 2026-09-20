package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.audit.AuditLog;
import com.chefcontrol.domain.repository.AuditLogRepository;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.infrastructure.persistence.PersistenceUtils;
import com.chefcontrol.infrastructure.persistence.entity.AuditLogJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.time.Instant;
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
    public void deleteByRestaurantId(UUID restaurantId) {
        jpa.deleteByRestaurantId(restaurantId);
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

    @Override
    public Page<AuditLog> search(UUID restaurantId, String actorEmail, AuditAction action, String entityType,
                                 Instant from, Instant to, PageRequest pageRequest) {
        // Specification en vez de "(:param IS NULL OR ...)" — con ese patrón Postgres no puede
        // inferir el tipo del bind param cuando el filtro correspondiente viene en null
        // (ERROR: could not determine data type of parameter $N). Acá el predicado directamente
        // no se agrega si el filtro es null, así que el problema no existe.
        Specification<AuditLogJpaEntity> spec = (root, query, cb) -> cb.equal(root.get("restaurantId"), restaurantId);
        if (actorEmail != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("actorEmail"), actorEmail));
        if (action != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action));
        if (entityType != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("entityType"), entityType));
        if (from != null) spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        if (to != null) spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));

        return PersistenceUtils.toDomain(
                jpa.findAll(spec, PersistenceUtils.toSpring(pageRequest, Sort.by("createdAt").descending()))
                   .map(AuditLogJpaEntity::toDomain));
    }
}
