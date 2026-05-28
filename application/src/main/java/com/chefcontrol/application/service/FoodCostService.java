package com.chefcontrol.application.service;

import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.finance.FoodCostMetric;
import com.chefcontrol.domain.repository.SaleRepository;
import com.chefcontrol.domain.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class FoodCostService {

    private final SaleRepository saleRepository;
    private final StockMovementRepository stockMovementRepository;

    public FoodCostReport calculate(Instant from, Instant to) {
        var restaurantId = TenantContext.require();

        BigDecimal revenue = saleRepository
                .sumTotalAmountByRestaurantIdAndSoldAtBetween(restaurantId, from, to);

        BigDecimal theoreticalCost = stockMovementRepository
                .sumSalesCost(restaurantId, from, to);

        FoodCostMetric metric = new FoodCostMetric(revenue, theoreticalCost);
        return new FoodCostReport(from, to, revenue, theoreticalCost, metric.percentage());
    }

    public record FoodCostReport(
            Instant from,
            Instant to,
            BigDecimal revenue,
            BigDecimal theoreticalCost,
            BigDecimal foodCostPercentage
    ) {}
}
