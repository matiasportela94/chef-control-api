package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.history.RecipeVersion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecipeVersionRepository {

    RecipeVersion save(RecipeVersion version);

    /** La receta que regía en {@code at}, o vacío si el plato no tenía receta todavía. */
    Optional<RecipeVersion> findByMenuItemIdAt(UUID menuItemId, Instant at);

    /** La última versión registrada, para no guardar una nueva cuando la receta no cambió. */
    Optional<RecipeVersion> findLatestByMenuItemId(UUID menuItemId);

    /** Las versiones que arrancaron dentro del período, para saber cuándo cambió la receta. */
    List<RecipeVersion> findByMenuItemIdBetween(UUID menuItemId, Instant from, Instant to);
}
