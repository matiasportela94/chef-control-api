package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.repository.MenuItemRepository;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.infrastructure.persistence.PersistenceUtils;
import com.chefcontrol.infrastructure.persistence.entity.MenuItemJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaMenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MenuItemRepositoryAdapter implements MenuItemRepository {

    private final JpaMenuItemRepository jpa;

    @Override
    public Page<MenuItem> findByRestaurantIdAndActiveTrue(UUID restaurantId, PageRequest pageRequest) {
        return PersistenceUtils.toDomain(
                jpa.findByRestaurantIdAndActiveTrue(restaurantId,
                        PersistenceUtils.toSpring(pageRequest, Sort.by("name").ascending()))
                   .map(MenuItemJpaEntity::toDomain));
    }

    @Override
    public Optional<MenuItem> findByIdAndRestaurantId(UUID id, UUID restaurantId) {
        return jpa.findByIdAndRestaurantId(id, restaurantId).map(MenuItemJpaEntity::toDomain);
    }

    @Override
    public MenuItem save(MenuItem menuItem) {
        return jpa.save(MenuItemJpaEntity.from(menuItem)).toDomain();
    }
}
