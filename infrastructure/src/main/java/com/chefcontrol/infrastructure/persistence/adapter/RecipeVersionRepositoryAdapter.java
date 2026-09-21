package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.history.RecipeVersion;
import com.chefcontrol.domain.repository.RecipeVersionRepository;
import com.chefcontrol.infrastructure.persistence.entity.RecipeVersionJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaRecipeVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RecipeVersionRepositoryAdapter implements RecipeVersionRepository {

    private final JpaRecipeVersionRepository jpa;

    @Override
    public RecipeVersion save(RecipeVersion version) {
        // Reload-after-save: los ítems son lazy y la entidad que devuelve save() no los trae.
        RecipeVersionJpaEntity saved = jpa.save(RecipeVersionJpaEntity.from(version));
        return jpa.findFirstByMenuItemIdOrderByValidFromDesc(saved.getMenuItemId())
                .map(RecipeVersionJpaEntity::toDomain)
                .orElseGet(saved::toDomain);
    }

    @Override
    public Optional<RecipeVersion> findByMenuItemIdAt(UUID menuItemId, Instant at) {
        return jpa.findFirstByMenuItemIdAndValidFromLessThanEqualOrderByValidFromDesc(menuItemId, at)
                .map(RecipeVersionJpaEntity::toDomain);
    }

    @Override
    public Optional<RecipeVersion> findLatestByMenuItemId(UUID menuItemId) {
        return jpa.findFirstByMenuItemIdOrderByValidFromDesc(menuItemId)
                .map(RecipeVersionJpaEntity::toDomain);
    }

    @Override
    public List<RecipeVersion> findByMenuItemIdBetween(UUID menuItemId, Instant from, Instant to) {
        return jpa.findByMenuItemIdAndValidFromBetweenOrderByValidFromAsc(menuItemId, from, to).stream()
                .map(RecipeVersionJpaEntity::toDomain)
                .toList();
    }
}
