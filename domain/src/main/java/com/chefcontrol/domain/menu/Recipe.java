package com.chefcontrol.domain.menu;

import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class Recipe {

    private UUID id;
    private UUID menuItemId;
    private UUID restaurantId;
    private int servings = 1;
    private Instant createdAt;

    @Builder.Default
    private List<RecipeItem> items = new ArrayList<>();

    /**
     * Replaces all items atomically. Called when the user saves a new version of the recipe.
     */
    public void replaceItems(List<RecipeItem> newItems) {
        this.items.clear();
        this.items.addAll(newItems);
    }

    /**
     * Scales this recipe's ingredients to produce the given number of portions.
     * Returns the exact ingredient quantities needed, ready to deduct from stock.
     */
    public List<RecipeIngredient> calculateIngredientsForQuantity(int quantity) {
        return items.stream()
                .map(ri -> new RecipeIngredient(
                        ri.getProductId(),
                        ri.getQuantity()
                                .multiply(BigDecimal.valueOf(quantity))
                                .divide(BigDecimal.valueOf(servings), 3, RoundingMode.HALF_UP),
                        ri.getUnitId()))
                .toList();
    }
}
