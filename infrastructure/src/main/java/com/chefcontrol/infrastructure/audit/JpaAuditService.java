package com.chefcontrol.infrastructure.audit;

import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.audit.AuditLog;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.repository.AuditLogRepository;
import com.chefcontrol.domain.security.ChefControlPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JpaAuditService implements AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Async("messageExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditAction action) {
        persist(action, null, null, null);
    }

    @Override
    @Async("messageExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditAction action, String entityType, UUID entityId) {
        persist(action, entityType, entityId, null);
    }

    @Override
    @Async("messageExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditAction action, String entityType, UUID entityId, Object payload) {
        persist(action, entityType, entityId, toJson(payload));
    }

    private void persist(AuditAction action, String entityType, UUID entityId, String payload) {
        try {
            ChefControlPrincipal principal = currentPrincipal();
            AuditLog entry = AuditLog.builder()
                    .actorId(principal != null ? principal.userId() : null)
                    .actorEmail(principal != null ? principal.email() : null)
                    .restaurantId(TenantContext.get())
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .payload(payload)
                    .build();
            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Audit must never break the main flow
            log.error("Failed to persist audit log [action={}]: {}", action, e.getMessage());
        }
    }

    private ChefControlPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof ChefControlPrincipal principal) {
            return principal;
        }
        return null;
    }

    private String toJson(Object payload) {
        if (payload == null) return null;
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize audit payload: {}", e.getMessage());
            return null;
        }
    }
}
