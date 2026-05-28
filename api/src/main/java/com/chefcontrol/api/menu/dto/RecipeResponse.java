package com.chefcontrol.api.menu.dto;

import com.chefcontrol.domain.menu.Recipe;
import com.chefcontrol.domain.menu.RecipeItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RecipeResponse(
        UUID id,
        UUID menuItemId,
        int servings,
        List<RecipeItemResponse> items
) {
    public record RecipeItemResponse(
            UUID id,
            UUID productId,
            String productName,
            BigDecimal quantity,
            UUID unitId,
            String unitName
    ) {
        static RecipeItemResponse from(RecipeItem ri) {
            return new RecipeItemResponse(
                    ri.getId(),
                    ri.getProductId(),
                    ri.getProductName(),
                    ri.getQuantity(),
                    ri.getUnitId(),
                    ri.getUnitName()
            );
        }
    }

    public static RecipeResponse from(Recipe recipe) {
        return new RecipeResponse(
                recipe.getId(),
                recipe.getMenuItemId(),
                recipe.getServings(),
                recipe.getItems().stream().map(RecipeItemResponse::from).toList()
        );
    }
}
