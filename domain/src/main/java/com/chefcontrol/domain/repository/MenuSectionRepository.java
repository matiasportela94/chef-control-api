package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.menu.MenuSection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuSectionRepository {

    /** Ordenadas por sortOrder: el orden en que se come, no el alfabético. */
    List<MenuSection> findAllByRestaurantId(UUID restaurantId);

    Optional<MenuSection> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    boolean existsByRestaurantIdAndNameIgnoreCase(UUID restaurantId, String name);

    MenuSection save(MenuSection section);

    void delete(UUID id);
}
