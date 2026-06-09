package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.repository.StockBatchAllocationRepository;
import com.chefcontrol.domain.stock.StockBatchAllocation;
import com.chefcontrol.infrastructure.persistence.entity.StockBatchAllocationJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaStockBatchAllocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class StockBatchAllocationRepositoryAdapter implements StockBatchAllocationRepository {

    private final JpaStockBatchAllocationRepository jpa;

    @Override
    public StockBatchAllocation save(StockBatchAllocation allocation) {
        return jpa.save(StockBatchAllocationJpaEntity.from(allocation)).toDomain();
    }

    @Override
    public List<StockBatchAllocation> findByStockMovementId(UUID stockMovementId) {
        return jpa.findByStockMovementId(stockMovementId).stream()
                .map(StockBatchAllocationJpaEntity::toDomain)
                .collect(Collectors.toList());
    }
}
