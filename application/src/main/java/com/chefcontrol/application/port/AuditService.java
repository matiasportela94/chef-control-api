package com.chefcontrol.application.port;

import com.chefcontrol.domain.audit.AuditAction;

import java.util.UUID;

/**
 * Application port for audit logging.
 * Reads actor and tenant from the SecurityContext — callers don't need to pass them.
 * All methods are fire-and-forget: async, non-blocking, never throw.
 */
public interface AuditService {

    void log(AuditAction action);

    void log(AuditAction action, String entityType, UUID entityId);

    void log(AuditAction action, String entityType, UUID entityId, Object payload);
}
