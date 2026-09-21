package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.history.PriceHistoryEntry;
import com.chefcontrol.domain.repository.PriceHistoryRepository;
import com.chefcontrol.infrastructure.persistence.entity.MenuItemPriceHistoryJpaEntity;
import com.chefcontrol.infrastructure.persistence.entity.ProductYieldHistoryJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaMenuItemPriceHistoryRepository;
import com.chefcontrol.infrastructure.persistence.jpa.JpaProductYieldHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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


    @Override
    public Optional<PriceHistoryEntry> findMenuItemPriceAt(UUID menuItemId, Instant at) {
        return menuItemJpa
                .findFirstByMenuItemIdAndValidFromLessThanEqualOrderByValidFromDesc(menuItemId, at)
                .map(MenuItemPriceHistoryJpaEntity::toDomain);
    }

    @Override
    public Optional<PriceHistoryEntry> findProductYieldAt(UUID productId, Instant at) {
        return productJpa
                .findFirstByProductIdAndValidFromLessThanEqualOrderByValidFromDesc(productId, at)
                .map(ProductYieldHistoryJpaEntity::toDomain);
    }

    @Override
    public List<PriceHistoryEntry> findMenuItemPriceHistory(UUID menuItemId) {
        return menuItemJpa.findByMenuItemIdOrderByValidFromAsc(menuItemId).stream()
                .map(MenuItemPriceHistoryJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<PriceHistoryEntry> findProductYieldHistory(UUID productId) {
        return productJpa.findByProductIdOrderByValidFromAsc(productId).stream()
                .map(ProductYieldHistoryJpaEntity::toDomain)
                .toList();
    }
}
