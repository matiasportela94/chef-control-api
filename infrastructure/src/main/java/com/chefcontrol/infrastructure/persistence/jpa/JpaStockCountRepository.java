package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.StockCountJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JpaStockCountRepository extends JpaRepository<StockCountJpaEntity, UUID> {

    Page<StockCountJpaEntity> findByRestaurantIdOrderByCountedAtDesc(UUID restaurantId, Pageable pageable);

    Optional<StockCountJpaEntity> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    @Query(value = "SELECT m.reference_id FROM stock_movements m WHERE m.reference_type = 'stock_count'"
            + " AND m.reference_id IN (:ids) GROUP BY m.reference_id HAVING bool_and(m.reversed_by IS NOT NULL)",
            nativeQuery = true)
    java.util.List<UUID> findReversedIds(@Param("ids") java.util.Collection<UUID> ids);
}
