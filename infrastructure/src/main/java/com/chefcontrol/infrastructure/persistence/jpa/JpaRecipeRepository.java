package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.RecipeJpaEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaRecipeRepository extends JpaRepository<RecipeJpaEntity, UUID> {

    @EntityGraph(attributePaths = {"menuItem", "items", "items.product", "items.unit"})
    Optional<RecipeJpaEntity> findByMenuItemIdAndRestaurantId(UUID menuItemId, UUID restaurantId);

    @EntityGraph(attributePaths = {"items", "items.product", "items.unit"})
    Optional<RecipeJpaEntity> findByProductIdAndRestaurantId(UUID productId, UUID restaurantId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT r.productId FROM RecipeJpaEntity r WHERE r.restaurantId = :restaurantId AND r.productId IS NOT NULL")
    java.util.Set<UUID> findPreparationProductIds(
            @org.springframework.data.repository.query.Param("restaurantId") UUID restaurantId);
}
