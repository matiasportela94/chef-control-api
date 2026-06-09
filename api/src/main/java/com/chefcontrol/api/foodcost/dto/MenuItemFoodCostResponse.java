package com.chefcontrol.api.foodcost.dto;

import com.chefcontrol.application.service.FoodCostService.MenuItemFoodCostReport;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MenuItemFoodCostResponse(
        UUID menuItemId,
        String menuItemName,
        Instant from,
        Instant to,
        int quantitySold,
        BigDecimal revenue,
        BigDecimal realizedCost,
        BigDecimal foodCostPercentage
) {
    public static MenuItemFoodCostResponse from(MenuItemFoodCostReport report) {
        return new MenuItemFoodCostResponse(
                report.menuItemId(), report.menuItemName(),
                report.from(), report.to(),
                report.quantitySold(), report.revenue(),
                report.realizedCost(), report.foodCostPercentage());
    }
}
