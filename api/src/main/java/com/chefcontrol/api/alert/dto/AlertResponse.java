package com.chefcontrol.api.alert.dto;

import com.chefcontrol.domain.alert.Alert;

import java.time.Instant;
import java.util.UUID;

public record AlertResponse(
        UUID id,
        UUID productId,
        String type,
        String severity,
        String message,
        boolean isRead,
        Instant resolvedAt,
        Instant createdAt
) {
    public static AlertResponse from(Alert a) {
        return new AlertResponse(
                a.getId(), a.getProductId(),
                a.getType().name(), a.getSeverity().name(),
                a.getMessage(), a.isRead(), a.getResolvedAt(), a.getCreatedAt());
    }
}
