package com.chefcontrol.domain.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Represents the food cost analysis for a given period.
 * Food cost % = (theoretical cost / revenue) * 100
 */
public record FoodCostMetric(BigDecimal revenue, BigDecimal theoreticalCost) {

    public BigDecimal percentage() {
        if (revenue.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return theoreticalCost
                .divide(revenue, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
