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
    public Optional<Recipe> findByProductIdAndRestaurantId(UUID productId, UUID restaurantId) {
        return jpa.findByProductIdAndRestaurantId(productId, restaurantId).map(RecipeJpaEntity::toDomain);
    }

    @Override
    public java.util.Set<UUID> findPreparationProductIds(UUID restaurantId) {
        return jpa.findPreparationProductIds(restaurantId);
    }

    @Override
    public Recipe save(Recipe recipe) {
        RecipeJpaEntity saved = jpa.save(RecipeJpaEntity.from(recipe));
        Optional<RecipeJpaEntity> reloaded = saved.getProductId() != null
                ? jpa.findByProductIdAndRestaurantId(saved.getProductId(), saved.getRestaurantId())
                : jpa.findByMenuItemIdAndRestaurantId(saved.getMenuItemId(), saved.getRestaurantId());
        return reloaded.map(RecipeJpaEntity::toDomain).orElse(saved.toDomain());
    }

    @Override
    public void delete(Recipe recipe) {
        jpa.deleteById(recipe.getId());
    }
}
