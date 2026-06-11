package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.StockMovementJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaStockMovementRepository extends JpaRepository<StockMovementJpaEntity, UUID> {

    @Query(value = """
            SELECT COALESCE(SUM(CASE WHEN direction = 'IN' THEN quantity ELSE -quantity END), 0)
            FROM stock_movements
            WHERE product_id = :productId AND restaurant_id = :restaurantId
            """, nativeQuery = true)
    BigDecimal getCurrentStock(@Param("productId") UUID productId,
                               @Param("restaurantId") UUID restaurantId);

    Page<StockMovementJpaEntity> findByRestaurantIdOrderByCreatedAtDesc(UUID restaurantId, Pageable pageable);

    Page<StockMovementJpaEntity> findByProductIdAndRestaurantIdOrderByCreatedAtDesc(UUID productId, UUID restaurantId, Pageable pageable);

    Optional<StockMovementJpaEntity> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    List<StockMovementJpaEntity> findByReferenceIdAndReferenceType(UUID referenceId, String referenceType);

    @Modifying
    @Query("UPDATE StockMovementJpaEntity sm SET sm.reversedBy = :reversalId WHERE sm.id = :id")
    void markReversed(@Param("id") UUID id, @Param("reversalId") UUID reversalId);

    @Query(value = """
            SELECT COALESCE(
                SUM(quantity * cost_per_unit) / NULLIF(SUM(quantity), 0), 0)
            FROM stock_movements
            WHERE product_id = :productId AND restaurant_id = :restaurantId
              AND type = 'PURCHASE' AND cost_per_unit IS NOT NULL
            """, nativeQuery = true)
    BigDecimal getWeightedAvgPurchaseCost(@Param("productId") UUID productId,
                                          @Param("restaurantId") UUID restaurantId);

    @Query(value = """
            SELECT COALESCE(SUM(quantity * cost_per_unit), 0)
            FROM stock_movements
            WHERE restaurant_id = :restaurantId AND type = 'SALE'
              AND cost_per_unit IS NOT NULL
              AND created_at BETWEEN :from AND :to
            """, nativeQuery = true)
    BigDecimal sumSalesCost(@Param("restaurantId") UUID restaurantId,
                            @Param("from") Instant from,
                            @Param("to") Instant to);

    @Modifying
    @Query(value = """
            UPDATE stock_movements
            SET cost_per_unit = :cost
            WHERE reference_id = :purchaseItemId AND reference_type = 'purchase_item'
            """, nativeQuery = true)
    void updatePurchaseCostPerUnit(@Param("purchaseItemId") UUID purchaseItemId,
                                   @Param("cost") java.math.BigDecimal cost);

    @Query(value = """
            SELECT cost_per_unit
            FROM stock_movements
            WHERE product_id = :productId AND restaurant_id = :restaurantId
              AND type = 'PURCHASE' AND cost_per_unit IS NOT NULL AND cost_per_unit > 0
            ORDER BY created_at DESC
            LIMIT 1
            """, nativeQuery = true)
    BigDecimal findLastPurchaseCostPerUnit(@Param("productId") UUID productId,
                                           @Param("restaurantId") UUID restaurantId);

    @Query(value = """
            SELECT COALESCE(SUM(sm.quantity * sm.cost_per_unit), 0)
            FROM stock_movements sm
            JOIN sale_items si ON si.id = sm.reference_id AND sm.reference_type = 'sale_item'
            JOIN sales s ON s.id = si.sale_id
            WHERE sm.restaurant_id = :restaurantId
              AND sm.type = 'SALE'
              AND sm.cost_per_unit IS NOT NULL
              AND si.menu_item_id = :menuItemId
              AND s.sold_at BETWEEN :from AND :to
            """, nativeQuery = true)
    BigDecimal sumSalesCostByMenuItemAndPeriod(@Param("menuItemId") UUID menuItemId,
                                               @Param("restaurantId") UUID restaurantId,
                                               @Param("from") Instant from,
                                               @Param("to") Instant to);
}
