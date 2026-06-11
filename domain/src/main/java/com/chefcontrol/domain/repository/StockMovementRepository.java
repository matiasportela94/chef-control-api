package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.domain.stock.StockMovement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockMovementRepository {

    BigDecimal getCurrentStock(UUID productId, UUID restaurantId);

    Page<StockMovement> findByRestaurantIdOrderByCreatedAtDesc(UUID restaurantId, PageRequest pageRequest);

    Page<StockMovement> findByProductIdAndRestaurantIdOrderByCreatedAtDesc(UUID productId, UUID restaurantId, PageRequest pageRequest);

    Optional<StockMovement> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    List<StockMovement> findByReferenceIdAndReferenceType(UUID referenceId, String referenceType);

    StockMovement save(StockMovement movement);

    void markReversed(UUID id, UUID reversalId);

    BigDecimal getWeightedAvgPurchaseCost(UUID productId, UUID restaurantId);

    BigDecimal sumSalesCost(UUID restaurantId, Instant from, Instant to);

    BigDecimal sumSalesCostByMenuItemAndPeriod(UUID menuItemId, UUID restaurantId, Instant from, Instant to);

    BigDecimal findLastPurchaseCostPerUnit(UUID productId, UUID restaurantId);

    void updatePurchaseCostPerUnit(UUID purchaseItemId, BigDecimal newCostPerUnit);
}
