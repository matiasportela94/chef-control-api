package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.StockBatchJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaStockBatchRepository extends JpaRepository<StockBatchJpaEntity, UUID> {

    Optional<StockBatchJpaEntity> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    @Query("""
            SELECT b FROM StockBatchJpaEntity b
            WHERE b.productId = :productId AND b.restaurantId = :restaurantId
              AND b.quantityRemaining > 0
            ORDER BY b.createdAt ASC
            """)
    List<StockBatchJpaEntity> findAvailableByProductFifo(@Param("productId") UUID productId,
                                                          @Param("restaurantId") UUID restaurantId);
}
