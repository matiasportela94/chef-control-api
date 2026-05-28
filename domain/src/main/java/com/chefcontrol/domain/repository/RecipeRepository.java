package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.menu.Recipe;

import java.util.Optional;
import java.util.UUID;

public interface RecipeRepository {

    Optional<Recipe> findByMenuItemIdAndRestaurantId(UUID menuItemId, UUID restaurantId);

    Recipe save(Recipe recipe);

    void delete(Recipe recipe);
}
