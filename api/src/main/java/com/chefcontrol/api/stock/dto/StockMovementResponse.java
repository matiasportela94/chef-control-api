package com.chefcontrol.api.stock.dto;

import com.chefcontrol.domain.stock.StockMovement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockMovementResponse(
        UUID id,
        UUID productId,
        String type,
        String direction,
        BigDecimal quantity,
        UUID unitId,
        BigDecimal costPerUnit,
        BigDecimal stockBefore,
        BigDecimal stockAfter,
        UUID referenceId,
        String referenceType,
        UUID reversedBy,
        UUID userId,
        String source,
        Instant createdAt
) {
    public static StockMovementResponse from(StockMovement m) {
        return new StockMovementResponse(
                m.getId(), m.getProductId(),
                m.getType().name(), m.getDirection().name(),
                m.getQuantity(), m.getUnitId(), m.getCostPerUnit(),
                m.getStockBefore(), m.getStockAfter(),
                m.getReferenceId(), m.getReferenceType(), m.getReversedBy(),
                m.getUserId(), m.getSource().name(), m.getCreatedAt());
    }
}
