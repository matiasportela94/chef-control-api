package com.chefcontrol.api.foodcost.dto;

import com.chefcontrol.application.service.FoodCostService.FoodCostReport;

import java.math.BigDecimal;
import java.time.Instant;

public record FoodCostResponse(
        Instant from,
        Instant to,
        BigDecimal revenue,
        BigDecimal theoreticalCost,
        BigDecimal foodCostPercentage
) {
    public static FoodCostResponse from(FoodCostReport report) {
        return new FoodCostResponse(
                report.from(),
                report.to(),
                report.revenue(),
                report.theoreticalCost(),
                report.foodCostPercentage()
        );
    }
}
