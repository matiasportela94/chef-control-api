package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.audit.AuditLog;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;

import java.time.Instant;
import java.util.UUID;

public interface AuditLogRepository {

    AuditLog save(AuditLog entry);

    /** Se usa al borrar un restaurante: su rastro es data de la cuenta y se va con el local. */
    void deleteByRestaurantId(UUID restaurantId);

    Page<AuditLog> findByActorIdOrderByCreatedAtDesc(UUID actorId, PageRequest pageRequest);

    Page<AuditLog> findByRestaurantIdOrderByCreatedAtDesc(UUID restaurantId, PageRequest pageRequest);

    Page<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, UUID entityId, PageRequest pageRequest);

    /**
     * Búsqueda paginada para la pantalla de auditoría. Cualquier filtro en {@code null} se ignora.
     */
    Page<AuditLog> search(UUID restaurantId, String actorEmail, AuditAction action, String entityType,
                          Instant from, Instant to, PageRequest pageRequest);
}
