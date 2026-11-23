package com.chefcontrol.api.stock.dto;

import com.chefcontrol.application.service.StockCountService.StockCountResult;
import com.chefcontrol.domain.stock.StockCount;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockCountResponse(
        UUID id,
        String notes,
        Instant countedAt,
        int itemsChecked,
        int adjustmentsMade,
        List<StockMovementResponse> adjustments,
        /** Tenía ajustes y ya no queda ninguno en pie: el stock volvió a como estaba antes de contar. */
        boolean reversed
) {
    public static StockCountResponse from(StockCountResult result) {
        StockCount c = result.count();
        List<StockMovementResponse> movements = result.movements().stream()
                .map(StockMovementResponse::from)
                .toList();
        return new StockCountResponse(
                c.getId(), c.getNotes(), c.getCountedAt(),
                c.getItemsChecked(), movements.size(), movements, result.reversed());
    }

    public static StockCountResponse summary(StockCount c, boolean reversed) {
        return new StockCountResponse(c.getId(), c.getNotes(), c.getCountedAt(),
                c.getItemsChecked(), c.getAdjustmentsMade(), null, reversed);
    }
}
