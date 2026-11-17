package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.menu.Recipe;

import java.util.Optional;
import java.util.UUID;

public interface RecipeRepository {

    Optional<Recipe> findByMenuItemIdAndRestaurantId(UUID menuItemId, UUID restaurantId);
    /** La receta de una preparación: el insumo es preparación si la tiene. */
    Optional<Recipe> findByProductIdAndRestaurantId(UUID productId, UUID restaurantId);
    /** Los insumos del restaurante que son preparaciones. */
    java.util.Set<UUID> findPreparationProductIds(UUID restaurantId);

    Recipe save(Recipe recipe);

    void delete(Recipe recipe);
}
