package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.StockCountJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaStockCountRepository extends JpaRepository<StockCountJpaEntity, UUID> {

    Page<StockCountJpaEntity> findByRestaurantIdOrderByCountedAtDesc(UUID restaurantId, Pageable pageable);

    Optional<StockCountJpaEntity> findByIdAndRestaurantId(UUID id, UUID restaurantId);
}
