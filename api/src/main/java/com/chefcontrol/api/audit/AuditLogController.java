package com.chefcontrol.api.audit;

import com.chefcontrol.api.audit.dto.AuditLogResponse;
import com.chefcontrol.api.shared.PagedResponse;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.repository.AuditLogRepository;
import com.chefcontrol.domain.shared.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_AUDIT_VIEW')")
    public ResponseEntity<PagedResponse<AuditLogResponse>> listAuditLogs(
                                                                         @RequestParam(defaultValue = "0") int page,
                                                                         @RequestParam(defaultValue = "20") int size,
                                                                         @RequestParam(required = false) String actorEmail,
                                                                         @RequestParam(required = false) AuditAction action,
                                                                         @RequestParam(required = false) String entityType,
                                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        var result = auditLogRepository.search(
                TenantContext.require(), actorEmail, action, entityType, from, to,
                PageRequest.of(page, size));
        return ResponseEntity.ok(PagedResponse.of(result.map(AuditLogResponse::from)));
    }
}
