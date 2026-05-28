package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;

import java.util.Optional;
import java.util.UUID;

public interface MenuItemRepository {

    Page<MenuItem> findByRestaurantIdAndActiveTrue(UUID restaurantId, PageRequest pageRequest);

    Optional<MenuItem> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    MenuItem save(MenuItem menuItem);
}
