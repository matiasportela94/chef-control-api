package com.chefcontrol.api.foodcost.dto;

import com.chefcontrol.application.service.FoodCostService.RecipeCostReport;
import com.chefcontrol.application.service.FoodCostService.RecipeIngredientCost;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RecipeCostResponse(
        UUID menuItemId,
        String menuItemName,
        int servings,
        BigDecimal menuPrice,
        List<IngredientCost> ingredients,
        BigDecimal totalCost,
        BigDecimal costPerServing,
        BigDecimal foodCostPercentage
) {
    public record IngredientCost(
            UUID productId,
            String productName,
            BigDecimal quantity,
            UUID unitId,
            String unitName,
            BigDecimal unitCost,
            BigDecimal totalCost
    ) {
        static IngredientCost from(RecipeIngredientCost ic) {
            return new IngredientCost(
                    ic.productId(), ic.productName(), ic.quantity(),
                    ic.unitId(), ic.unitName(), ic.unitCost(), ic.totalCost());
        }
    }

    public static RecipeCostResponse from(RecipeCostReport report) {
        return new RecipeCostResponse(
                report.menuItemId(),
                report.menuItemName(),
                report.servings(),
                report.menuPrice(),
                report.ingredients().stream().map(IngredientCost::from).toList(),
                report.totalCost(),
                report.costPerServing(),
                report.foodCostPercentage());
    }
}
