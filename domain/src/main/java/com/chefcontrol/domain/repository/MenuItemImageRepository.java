package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.menu.MenuItemImage;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface MenuItemImageRepository {

    Optional<MenuItemImage> findByMenuItemId(UUID menuItemId);

    /**
     * Cuando cambio la foto de cada plato, sin traer los bytes. Lo usa el listado para decir
     * si hay imagen y romper la cache del navegador cuando cambia.
     */
    Map<UUID, Instant> findUpdatedAtByMenuItemIds(Set<UUID> menuItemIds);

    MenuItemImage save(MenuItemImage image);

    void deleteByMenuItemId(UUID menuItemId);
}
