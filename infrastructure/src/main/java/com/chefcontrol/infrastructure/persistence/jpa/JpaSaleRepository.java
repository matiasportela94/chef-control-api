package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.SaleJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface JpaSaleRepository extends JpaRepository<SaleJpaEntity, UUID> {

    Page<SaleJpaEntity> findByRestaurantIdOrderBySoldAtDesc(UUID restaurantId, Pageable pageable);

    Optional<SaleJpaEntity> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM SaleJpaEntity s WHERE s.restaurantId = :restaurantId AND s.soldAt BETWEEN :from AND :to")
    BigDecimal sumTotalAmountByRestaurantIdAndSoldAtBetween(
            @Param("restaurantId") UUID restaurantId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    long countByRestaurantIdAndSoldAtBetween(UUID restaurantId, Instant from, Instant to);
}
