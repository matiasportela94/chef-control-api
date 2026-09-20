package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.stock.StockBatch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

    /**
     * Fecha de vencimiento más próxima por producto, mirando solo lotes con stock remanente.
     * Los productos sin ningún lote con fecha no aparecen en el mapa.
     */
    Map<UUID, LocalDate> findNextExpirationByProduct(UUID restaurantId);

    /**
     * Batches with remaining stock whose expiration date is on or before {@code maxDate}
     * (includes already-expired batches), across all restaurants — for the nightly alert sweep.
     */
    List<StockBatch> findExpiringSoon(LocalDate maxDate);
}
