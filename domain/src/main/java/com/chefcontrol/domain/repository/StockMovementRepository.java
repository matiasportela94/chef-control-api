package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.domain.stock.StockMovement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    /** Ídem, mirando solo las compras hasta {@code at} — para costear un período pasado. */
    BigDecimal getWeightedAvgPurchaseCostAsOf(UUID productId, UUID restaurantId, Instant at);

    /** Cuándo entró cada compra del producto en el período: los puntos donde su costo cambió. */
    List<Instant> findPurchaseDates(UUID productId, UUID restaurantId, Instant from, Instant to);

    /** Merma estándar vs. registrada por insumo desde {@code from}, en todos los restaurantes. */
    List<ProductWasteSummary> sumWasteByProductSince(Instant from);

    BigDecimal sumSalesCost(UUID restaurantId, Instant from, Instant to);

    BigDecimal sumSalesCostByMenuItemAndPeriod(UUID menuItemId, UUID restaurantId, Instant from, Instant to);

    BigDecimal findLastPurchaseCostPerUnit(UUID productId, UUID restaurantId);

    void updatePurchaseCostPerUnit(UUID purchaseItemId, BigDecimal newCostPerUnit);

    Map<UUID, BigDecimal> getAllCurrentStocks(UUID restaurantId);
}
