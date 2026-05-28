package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.audit.AuditLog;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;

import java.util.UUID;

public interface AuditLogRepository {

    AuditLog save(AuditLog entry);

    Page<AuditLog> findByActorIdOrderByCreatedAtDesc(UUID actorId, PageRequest pageRequest);

    Page<AuditLog> findByRestaurantIdOrderByCreatedAtDesc(UUID restaurantId, PageRequest pageRequest);

    Page<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, UUID entityId, PageRequest pageRequest);
}
