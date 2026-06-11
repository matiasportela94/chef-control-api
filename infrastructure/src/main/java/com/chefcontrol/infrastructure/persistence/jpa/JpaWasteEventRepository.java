package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.WasteEventJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface JpaWasteEventRepository extends JpaRepository<WasteEventJpaEntity, UUID> {

    @EntityGraph(attributePaths = {"product", "unit"})
    Page<WasteEventJpaEntity> findByRestaurantIdOrderByCreatedAtDesc(UUID restaurantId, Pageable pageable);

    @EntityGraph(attributePaths = {"product", "unit"})
    Optional<WasteEventJpaEntity> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    long countByRestaurantIdAndCreatedAtGreaterThanEqual(UUID restaurantId, Instant since);

    @Query("SELECT COALESCE(SUM(w.cost), 0) FROM WasteEventJpaEntity w WHERE w.restaurantId = :restaurantId AND w.createdAt BETWEEN :from AND :to")
    BigDecimal sumCostByRestaurantIdAndCreatedAtBetween(
            @Param("restaurantId") UUID restaurantId,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
