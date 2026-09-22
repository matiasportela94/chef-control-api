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

    /**
     * Ídem, pero mirando solo las compras hasta {@code at}. Es lo que hace que el food cost
     * teórico de un período pasado use los precios de ese momento y no los de hoy: sin el corte,
     * preguntar "¿cuánto costaba en marzo?" promediaba también las compras de abril en adelante.
     */
    @Query(value = """
            SELECT COALESCE(
                SUM(quantity * cost_per_unit) / NULLIF(SUM(quantity), 0), 0)
            FROM stock_movements
            WHERE product_id = :productId AND restaurant_id = :restaurantId
              AND type = 'PURCHASE' AND cost_per_unit IS NOT NULL
              AND created_at <= :at
            """, nativeQuery = true)
    BigDecimal getWeightedAvgPurchaseCostAsOf(@Param("productId") UUID productId,
                                              @Param("restaurantId") UUID restaurantId,
                                              @Param("at") Instant at);

    /**
     * Costo de lo vendido en el período: el SALE de cada receta más la merma estándar de limpieza
     * que ese mismo consumo generó. La merma estándar cuelga del sale_item, así que el filtro por
     * reference_type la separa de la merma registrada a mano (vencidos, robo, daño), que no es
     * costo de lo vendido. Sin el OR, el teórico sube con el rendimiento y el realizado se queda
     * quieto: la brecha entre ambos sería un artefacto nuestro, no un desvío real.
     */
    @Query(value = """
            SELECT COALESCE(SUM(quantity * cost_per_unit), 0)
            FROM stock_movements
            WHERE restaurant_id = :restaurantId
              AND (type = 'SALE' OR (type = 'WASTE' AND reference_type = 'sale_item'))
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

    /** Ídem {@link #sumSalesCost}, por plato. Acá el join por reference_type = 'sale_item' ya acota la merma. */
    @Query(value = """
            SELECT COALESCE(SUM(sm.quantity * sm.cost_per_unit), 0)
            FROM stock_movements sm
            JOIN sale_items si ON si.id = sm.reference_id AND sm.reference_type = 'sale_item'
            JOIN sales s ON s.id = si.sale_id
            WHERE sm.restaurant_id = :restaurantId
              AND sm.type IN ('SALE', 'WASTE')
              AND sm.cost_per_unit IS NOT NULL
              AND si.menu_item_id = :menuItemId
              AND s.sold_at BETWEEN :from AND :to
            """, nativeQuery = true)
    BigDecimal sumSalesCostByMenuItemAndPeriod(@Param("menuItemId") UUID menuItemId,
                                               @Param("restaurantId") UUID restaurantId,
                                               @Param("from") Instant from,
                                               @Param("to") Instant to);

    /**
     * Merma por insumo en el período, partida entre la estándar (de limpieza, cuelga del
     * sale_item) y la registrada a mano (cuelga del waste_event).
     *
     * <p>{@code reversed_by IS NULL} no es opcional: el ledger es append-only, así que revertir
     * una merma deja el movimiento original en su lugar y agrega uno de tipo REVERSAL. Sin el
     * filtro, una merma cargada por error y corregida seguiría contando para la alerta.
     *
     * <p>El HAVING deja afuera los productos sin merma estándar en el período — los que nadie
     * vendió, o a los que todavía no les cargaron rendimiento. Sin eso, el esperado es cero,
     * cualquier merma lo supera y la alerta dispara para todo el catálogo.
     */
    @Query(value = """
            SELECT restaurant_id,
                   product_id,
                   COALESCE(SUM(CASE WHEN reference_type = 'sale_item'   THEN quantity ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN reference_type = 'waste_event' THEN quantity ELSE 0 END), 0)
            FROM stock_movements
            WHERE type = 'WASTE'
              AND reversed_by IS NULL
              AND created_at >= :from
            GROUP BY restaurant_id, product_id
            HAVING SUM(CASE WHEN reference_type = 'sale_item' THEN quantity ELSE 0 END) > 0
            """, nativeQuery = true)
    List<Object[]> sumWasteByProductSince(@Param("from") Instant from);

    /**
     * Los momentos en que entró una compra de este producto dentro del período. Cada una mueve el
     * promedio ponderado, así que son los puntos donde el costo del insumo cambió de verdad.
     */
    @Query(value = """
            SELECT created_at
            FROM stock_movements
            WHERE product_id = :productId AND restaurant_id = :restaurantId
              AND type = 'PURCHASE' AND cost_per_unit IS NOT NULL
              AND created_at BETWEEN :from AND :to
            ORDER BY created_at
            """, nativeQuery = true)
    List<Instant> findPurchaseDates(@Param("productId") UUID productId,
                                    @Param("restaurantId") UUID restaurantId,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to);

    @Query(value = """
            SELECT product_id,
                   COALESCE(SUM(CASE WHEN direction = 'IN' THEN quantity ELSE -quantity END), 0)
            FROM stock_movements
            WHERE restaurant_id = :restaurantId
            GROUP BY product_id
            """, nativeQuery = true)
    List<Object[]> getAllCurrentStocksRaw(@Param("restaurantId") UUID restaurantId);
}
