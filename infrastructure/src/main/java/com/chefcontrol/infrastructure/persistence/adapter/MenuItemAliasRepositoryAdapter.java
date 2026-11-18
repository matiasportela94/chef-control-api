package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.repository.MenuItemAliasRepository;
import com.chefcontrol.domain.sale.MenuItemAlias;
import com.chefcontrol.infrastructure.persistence.entity.MenuItemAliasJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaMenuItemAliasRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MenuItemAliasRepositoryAdapter implements MenuItemAliasRepository {

    private final JpaMenuItemAliasRepository jpa;

    @Override
    public List<MenuItemAlias> findByRestaurantId(UUID restaurantId) {
        return jpa.findByRestaurantId(restaurantId).stream()
                .map(e -> new MenuItemAlias(e.getId(), e.getRestaurantId(), e.getPrintedKey(), e.getMenuItemId())).toList();
    }

    @Override
    public void upsert(MenuItemAlias a) {
        MenuItemAliasJpaEntity e = jpa.findByRestaurantIdAndPrintedKey(a.restaurantId(), a.printedKey())
                .orElseGet(MenuItemAliasJpaEntity::new);
        e.setRestaurantId(a.restaurantId());
        e.setPrintedKey(a.printedKey());
        e.setMenuItemId(a.menuItemId());
        e.setUpdatedAt(Instant.now());
        jpa.save(e);
    }
}
