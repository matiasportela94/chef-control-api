package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.RecipeVersionJpaEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaRecipeVersionRepository extends JpaRepository<RecipeVersionJpaEntity, UUID> {

    /** La versión vigente en {@code at}: la última que arrancó antes o en ese momento. */
    @EntityGraph(attributePaths = "items")
    Optional<RecipeVersionJpaEntity> findFirstByMenuItemIdAndValidFromLessThanEqualOrderByValidFromDesc(
            UUID menuItemId, Instant at);

    @EntityGraph(attributePaths = "items")
    Optional<RecipeVersionJpaEntity> findFirstByMenuItemIdOrderByValidFromDesc(UUID menuItemId);

    @EntityGraph(attributePaths = "items")
    List<RecipeVersionJpaEntity> findByMenuItemIdAndValidFromBetweenOrderByValidFromAsc(
            UUID menuItemId, Instant from, Instant to);
}
