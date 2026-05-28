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
        List<StockMovementResponse> adjustments
) {
    public static StockCountResponse from(StockCountResult result) {
        StockCount c = result.count();
        List<StockMovementResponse> movements = result.movements().stream()
                .map(StockMovementResponse::from)
                .toList();
        return new StockCountResponse(
                c.getId(), c.getNotes(), c.getCountedAt(),
                result.movements().size(), movements.size(), movements);
    }

    public static StockCountResponse summary(StockCount c) {
        return new StockCountResponse(c.getId(), c.getNotes(), c.getCountedAt(), 0, 0, List.of());
    }
}
