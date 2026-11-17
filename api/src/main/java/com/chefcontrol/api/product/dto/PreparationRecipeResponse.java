package com.chefcontrol.api.product.dto;

import com.chefcontrol.domain.menu.Recipe;
import com.chefcontrol.domain.product.Product;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** La receta de una tanda; el rendimiento va en la unidad por defecto de la preparación. */
public record PreparationRecipeResponse(
        UUID productId,
        BigDecimal yieldQuantity,
        UUID yieldUnitId,
        String yieldUnitName,
        List<Item> items
) {
    public record Item(UUID productId, String productName, BigDecimal quantity, UUID unitId, String unitName) {}

    public static PreparationRecipeResponse from(Recipe recipe, Product product) {
        return new PreparationRecipeResponse(product.getId(), recipe.getYieldQuantity(), product.getDefaultUnitId(),
                product.getDefaultUnitName(), recipe.getItems().stream()
                .map(i -> new Item(i.getProductId(), i.getProductName(), i.getQuantity(), i.getUnitId(), i.getUnitName()))
                .toList());
    }
}
