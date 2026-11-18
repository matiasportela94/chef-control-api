package com.chefcontrol.api.waste.dto;

import com.chefcontrol.domain.waste.WasteEvent;
import com.chefcontrol.domain.waste.WasteReason;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WasteEventResponse(
        UUID id,
        UUID productId,
        String productName,
        String productSku,
        BigDecimal quantity,
        UUID unitId,
        String unitName,
        String unitAbbreviation,
        WasteReason reason,
        BigDecimal cost,
        UUID userId,
        Instant createdAt,
        /** Deshecha: su movimiento se revirtió (desde el chat o /stock-movements). Sigue en la lista; no suma en el tablero. */
        boolean reversed
) {
    public static WasteEventResponse from(WasteEvent event) {
        return from(event, false);
    }

    public static WasteEventResponse from(WasteEvent event, boolean reversed) {
        return new WasteEventResponse(
                event.getId(),
                event.getProductId(),
                event.getProductName(),
                event.getProductSku(),
                event.getQuantity(),
                event.getUnitId(),
                event.getUnitName(),
                event.getUnitAbbreviation(),
                event.getReason(),
                event.getCost(),
                event.getUserId(),
                event.getCreatedAt(),
                reversed);
    }
}
