package com.chefcontrol.domain.history;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * La composición que tenía una receta desde {@code validFrom} hasta la versión siguiente.
 *
 * <p>Existe porque {@code saveRecipe()} reemplaza los ítems en lugar de versionarlos: sin esto,
 * el food cost de un período pasado se calcularía con la receta de hoy.
 */
public record RecipeVersion(
        UUID id,
        UUID restaurantId,
        UUID menuItemId,
        int servings,
        Instant validFrom,
        UUID changedBy,
        List<Item> items
) {
    public record Item(UUID productId, BigDecimal quantity, UUID unitId) {}

    public static RecipeVersion of(UUID restaurantId, UUID menuItemId, int servings,
                                   UUID changedBy, List<Item> items) {
        return new RecipeVersion(null, restaurantId, menuItemId, servings, Instant.now(), changedBy, items);
    }

    /**
     * Dos versiones son la misma receta si tienen el mismo rendimiento por porción y los mismos
     * ítems, sin importar el orden en que estén guardados — el orden de los ingredientes no es
     * parte de la receta y un reordenamiento no merece una versión nueva.
     */
    public boolean sameContentAs(RecipeVersion other) {
        if (other == null || other.servings != servings || other.items.size() != items.size()) {
            return false;
        }
        return items.stream().allMatch(item -> other.items.stream().anyMatch(o ->
                o.productId().equals(item.productId())
                        && o.unitId().equals(item.unitId())
                        && o.quantity().compareTo(item.quantity()) == 0));
    }
}
