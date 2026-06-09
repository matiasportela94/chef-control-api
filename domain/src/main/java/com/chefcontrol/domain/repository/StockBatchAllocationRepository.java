package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.stock.StockBatchAllocation;

import java.util.List;
import java.util.UUID;

public interface StockBatchAllocationRepository {

    StockBatchAllocation save(StockBatchAllocation allocation);

    List<StockBatchAllocation> findByStockMovementId(UUID stockMovementId);
}
