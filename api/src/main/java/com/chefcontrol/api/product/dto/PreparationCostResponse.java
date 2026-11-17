package com.chefcontrol.api.product.dto;

import com.chefcontrol.api.foodcost.dto.RecipeCostResponse.IngredientCost;
import com.chefcontrol.application.service.FoodCostService.PreparationCostReport;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** {@code unitCost} = {@code batchCost} / {@code yieldQuantity}: lo que cuesta una unidad de la preparación. */
public record PreparationCostResponse(
        UUID productId,
        BigDecimal yieldQuantity,
        UUID unitId,
        String unitName,
        BigDecimal batchCost,
        BigDecimal unitCost,
        List<IngredientCost> ingredients
) {
    public static PreparationCostResponse from(PreparationCostReport r) {
        return new PreparationCostResponse(r.productId(), r.yieldQuantity(), r.unitId(), r.unitName(), r.batchCost(),
                r.unitCost(), r.ingredients().stream().map(IngredientCost::from).toList());
    }
}
