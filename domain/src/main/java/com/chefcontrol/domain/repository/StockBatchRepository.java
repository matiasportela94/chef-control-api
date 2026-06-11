package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.stock.StockBatch;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockBatchRepository {

    StockBatch save(StockBatch batch);

    Optional<StockBatch> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    /**
     * Batches with remaining stock for a product, oldest first (FIFO consumption order).
     */
    List<StockBatch> findAvailableByProductFifo(UUID productId, UUID restaurantId);

    void updateCostPerUnitByPurchaseItemId(UUID purchaseItemId, BigDecimal newCostPerUnit);

    Optional<StockBatch> findByPurchaseItemId(UUID purchaseItemId);

    void zeroQuantityRemainingByPurchaseItemId(UUID purchaseItemId);
}
