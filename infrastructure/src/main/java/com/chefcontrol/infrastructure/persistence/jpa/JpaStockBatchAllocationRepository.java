package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.StockBatchAllocationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaStockBatchAllocationRepository extends JpaRepository<StockBatchAllocationJpaEntity, UUID> {

    List<StockBatchAllocationJpaEntity> findByStockMovementId(UUID stockMovementId);
}
