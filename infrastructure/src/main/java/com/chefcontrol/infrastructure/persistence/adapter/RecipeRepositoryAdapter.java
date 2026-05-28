package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.menu.Recipe;
import com.chefcontrol.domain.repository.RecipeRepository;
import com.chefcontrol.infrastructure.persistence.entity.RecipeJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaRecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RecipeRepositoryAdapter implements RecipeRepository {

    private final JpaRecipeRepository jpa;

    @Override
    public Optional<Recipe> findByMenuItemIdAndRestaurantId(UUID menuItemId, UUID restaurantId) {
        return jpa.findByMenuItemIdAndRestaurantId(menuItemId, restaurantId)
                .map(RecipeJpaEntity::toDomain);
    }

    @Override
    public Recipe save(Recipe recipe) {
        RecipeJpaEntity saved = jpa.save(RecipeJpaEntity.from(recipe));
        return jpa.findByMenuItemIdAndRestaurantId(saved.getMenuItemId(), saved.getRestaurantId())
                .map(RecipeJpaEntity::toDomain)
                .orElse(saved.toDomain());
    }

    @Override
    public void delete(Recipe recipe) {
        jpa.deleteById(recipe.getId());
    }
}
