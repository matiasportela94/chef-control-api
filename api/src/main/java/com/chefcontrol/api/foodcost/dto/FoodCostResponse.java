package com.chefcontrol.api.foodcost.dto;

import com.chefcontrol.application.service.FoodCostService.FoodCostReport;

import java.math.BigDecimal;
import java.time.Instant;

public record FoodCostResponse(
        Instant from,
        Instant to,
        /** Lo cobrado, con IVA. */
        BigDecimal revenue,
        /** Sin el IVA de las ventas (en un RI). El porcentaje va sobre este. */
        BigDecimal netRevenue,
        BigDecimal theoreticalCost,
        BigDecimal foodCostPercentage
) {
    public static FoodCostResponse from(FoodCostReport report) {
        return new FoodCostResponse(
                report.from(),
                report.to(),
                report.revenue(),
                report.netRevenue(),
                report.theoreticalCost(),
                report.foodCostPercentage()
        );
    }
}
