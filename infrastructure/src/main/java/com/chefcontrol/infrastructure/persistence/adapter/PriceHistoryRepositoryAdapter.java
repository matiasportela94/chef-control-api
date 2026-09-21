package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.history.PriceHistoryEntry;
import com.chefcontrol.domain.repository.PriceHistoryRepository;
import com.chefcontrol.infrastructure.persistence.entity.MenuItemPriceHistoryJpaEntity;
import com.chefcontrol.infrastructure.persistence.entity.ProductYieldHistoryJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaMenuItemPriceHistoryRepository;
import com.chefcontrol.infrastructure.persistence.jpa.JpaProductYieldHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PriceHistoryRepositoryAdapter implements PriceHistoryRepository {

    private final JpaMenuItemPriceHistoryRepository menuItemJpa;
    private final JpaProductYieldHistoryRepository productJpa;

    @Override
    public PriceHistoryEntry saveMenuItemPrice(PriceHistoryEntry entry) {
        return menuItemJpa.save(MenuItemPriceHistoryJpaEntity.from(entry)).toDomain();
    }

    @Override
    public PriceHistoryEntry saveProductYield(PriceHistoryEntry entry) {
        return productJpa.save(ProductYieldHistoryJpaEntity.from(entry)).toDomain();
    }

}
