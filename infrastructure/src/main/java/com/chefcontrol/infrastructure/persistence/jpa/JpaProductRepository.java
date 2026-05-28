package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.domain.repository.ProductStockProjection;
import com.chefcontrol.infrastructure.persistence.entity.ProductJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaProductRepository extends JpaRepository<ProductJpaEntity, UUID> {

    @EntityGraph(attributePaths = {"category", "defaultUnit"})
    Page<ProductJpaEntity> findByRestaurantIdAndIsActiveTrue(UUID restaurantId, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "defaultUnit"})
    Optional<ProductJpaEntity> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    boolean existsByRestaurantIdAndSku(UUID restaurantId, String sku);

    boolean existsByCategoryId(UUID categoryId);

    long countByRestaurantIdAndIsActiveTrue(UUID restaurantId);

    @Query(value = """
            SELECT p.id,
                   p.name,
                   p.sku,
                   u.abbreviation AS unit_abbreviation,
                   COALESCE(SUM(CASE WHEN sm.direction = 'IN' THEN sm.quantity ELSE -sm.quantity END), 0) AS current_stock,
                   p.min_stock,
                   p.max_stock
            FROM products p
            JOIN units u ON u.id = p.default_unit_id
            LEFT JOIN stock_movements sm ON sm.product_id = p.id AND sm.restaurant_id = :restaurantId
            WHERE p.restaurant_id = :restaurantId AND p.is_active = true
            GROUP BY p.id, p.name, p.sku, u.abbreviation, p.min_stock, p.max_stock
            ORDER BY p.name
            """, nativeQuery = true)
    List<ProductStockProjection> findProductStockByRestaurant(@Param("restaurantId") UUID restaurantId);
}
