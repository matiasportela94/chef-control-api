package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.PurchaseJpaEntity;
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

public interface JpaPurchaseRepository extends JpaRepository<PurchaseJpaEntity, UUID> {

    @EntityGraph(attributePaths = {"supplier"})
    Page<PurchaseJpaEntity> findByRestaurantIdOrderByPurchasedAtDesc(UUID restaurantId, Pageable pageable);

    @EntityGraph(attributePaths = {"supplier"})
    Optional<PurchaseJpaEntity> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    long countByRestaurantIdAndPurchasedAtGreaterThanEqual(UUID restaurantId, Instant since);

    @Query("SELECT COALESCE(SUM(p.total), 0) FROM PurchaseJpaEntity p WHERE p.restaurantId = :restaurantId AND p.purchasedAt >= :since")
    BigDecimal sumTotalByRestaurantIdAndPurchasedAtSince(
            @Param("restaurantId") UUID restaurantId,
            @Param("since") Instant since);
}
