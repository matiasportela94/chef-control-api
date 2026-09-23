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
    /**
     * {@code quantity} es lo que dice la receta; {@code grossQuantity} lo que hay que comprar
     * para tenerlo, ya dividido por el rendimiento, y <b>en la misma unidad</b>. El costo se cobra
     * sobre el bruto, así que sin las dos cantidades el desglose no explica de dónde sale el subtotal.
     */
    public record IngredientCost(
            UUID productId,
            String productName,
            BigDecimal quantity,
            UUID unitId,
            String unitName,
            BigDecimal grossQuantity,
            BigDecimal yieldPercentage,
            BigDecimal unitCost,
            BigDecimal totalCost
    ) {
        static IngredientCost from(RecipeIngredientCost ic) {
            return new IngredientCost(
                    ic.productId(), ic.productName(), ic.quantity(),
                    ic.unitId(), ic.unitName(), ic.grossQuantity(), ic.yieldPercentage(),
                    ic.unitCost(), ic.totalCost());
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
