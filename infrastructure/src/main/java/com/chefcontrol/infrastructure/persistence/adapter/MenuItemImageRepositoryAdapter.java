package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.menu.MenuItemImage;
import com.chefcontrol.domain.repository.MenuItemImageRepository;
import com.chefcontrol.infrastructure.persistence.entity.MenuItemImageJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaMenuItemImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MenuItemImageRepositoryAdapter implements MenuItemImageRepository {

    private final JpaMenuItemImageRepository jpa;

    @Override
    public Optional<MenuItemImage> findByMenuItemId(UUID menuItemId) {
        return jpa.findById(menuItemId).map(MenuItemImageJpaEntity::toDomain);
    }

    @Override
    public Map<UUID, Instant> findUpdatedAtByMenuItemIds(Set<UUID> menuItemIds) {
        if (menuItemIds.isEmpty()) return Map.of();
        Map<UUID, Instant> result = new HashMap<>();
        for (Object[] row : jpa.findUpdatedAtByMenuItemIds(menuItemIds)) {
            result.put((UUID) row[0], (Instant) row[1]);
        }
        return result;
    }

    @Override
    public MenuItemImage save(MenuItemImage image) {
        return jpa.save(MenuItemImageJpaEntity.from(image)).toDomain();
    }

    @Override
    public void deleteByMenuItemId(UUID menuItemId) {
        jpa.deleteById(menuItemId);
    }
}
