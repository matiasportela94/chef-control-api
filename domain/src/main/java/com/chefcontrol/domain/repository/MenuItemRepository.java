package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface MenuItemRepository {

    Page<MenuItem> findByRestaurantIdAndActive(UUID restaurantId, boolean active, PageRequest pageRequest);

    /** Los platos de una carta: el filtro por ids sale de carta_items. */
    Page<MenuItem> findByRestaurantIdAndActiveAndIdIn(UUID restaurantId, boolean active,
                                                      Collection<UUID> ids, PageRequest pageRequest);

    Optional<MenuItem> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    /** Para bloquear el borrado de un paso que todavía tiene platos. */
    boolean existsBySectionId(UUID sectionId);

    MenuItem save(MenuItem menuItem);
}
