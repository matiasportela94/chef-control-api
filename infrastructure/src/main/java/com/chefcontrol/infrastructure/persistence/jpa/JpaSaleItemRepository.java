package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.SaleItemJpaEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface JpaSaleItemRepository extends JpaRepository<SaleItemJpaEntity, UUID> {

    @EntityGraph(attributePaths = {"menuItem"})
    List<SaleItemJpaEntity> findBySaleId(UUID saleId);

    @EntityGraph(attributePaths = {"menuItem"})
    java.util.Optional<SaleItemJpaEntity> findWithRelationshipsById(UUID id);

    long countBySaleId(UUID saleId);

    @Query(value = """
            SELECT COALESCE(SUM(si.quantity * si.unit_price), 0)
            FROM sale_items si
            JOIN sales s ON s.id = si.sale_id
            WHERE s.restaurant_id = :restaurantId
              AND si.menu_item_id = :menuItemId
              AND s.sold_at BETWEEN :from AND :to
            """, nativeQuery = true)
    BigDecimal sumRevenueByMenuItemAndPeriod(@Param("menuItemId") UUID menuItemId,
                                             @Param("restaurantId") UUID restaurantId,
                                             @Param("from") Instant from,
                                             @Param("to") Instant to);

    @Query(value = """
            SELECT COALESCE(SUM(si.quantity), 0)
            FROM sale_items si
            JOIN sales s ON s.id = si.sale_id
            WHERE s.restaurant_id = :restaurantId
              AND si.menu_item_id = :menuItemId
              AND s.sold_at BETWEEN :from AND :to
            """, nativeQuery = true)
    int sumQuantitySoldByMenuItemAndPeriod(@Param("menuItemId") UUID menuItemId,
                                           @Param("restaurantId") UUID restaurantId,
                                           @Param("from") Instant from,
                                           @Param("to") Instant to);
}
