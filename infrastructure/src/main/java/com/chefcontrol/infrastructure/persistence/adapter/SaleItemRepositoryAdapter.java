package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.repository.SaleItemRepository;
import com.chefcontrol.domain.sale.SaleItem;
import com.chefcontrol.infrastructure.persistence.entity.SaleItemJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaSaleItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class SaleItemRepositoryAdapter implements SaleItemRepository {

    private final JpaSaleItemRepository jpa;

    @Override
    public List<SaleItem> findBySaleId(UUID saleId) {
        return jpa.findBySaleId(saleId).stream()
                .map(SaleItemJpaEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public int countBySaleId(UUID saleId) {
        return (int) jpa.countBySaleId(saleId);
    }

    @Override
    public SaleItem save(SaleItem saleItem) {
        SaleItemJpaEntity saved = jpa.save(SaleItemJpaEntity.from(saleItem));
        return jpa.findWithRelationshipsById(saved.getId())
                .map(SaleItemJpaEntity::toDomain)
                .orElse(saved.toDomain());
    }

    @Override
    public BigDecimal sumRevenueByMenuItemAndPeriod(UUID menuItemId, UUID restaurantId, Instant from, Instant to) {
        return jpa.sumRevenueByMenuItemAndPeriod(menuItemId, restaurantId, from, to);
    }

    @Override
    public int sumQuantitySoldByMenuItemAndPeriod(UUID menuItemId, UUID restaurantId, Instant from, Instant to) {
        return jpa.sumQuantitySoldByMenuItemAndPeriod(menuItemId, restaurantId, from, to);
    }
}
