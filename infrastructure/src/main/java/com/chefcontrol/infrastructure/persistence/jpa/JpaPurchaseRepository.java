package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.domain.purchase.PurchaseStatus;
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
    @Query("SELECT p FROM PurchaseJpaEntity p LEFT JOIN FETCH p.supplier WHERE p.restaurantId = :restaurantId AND p.status = 'ACTIVE' ORDER BY p.purchasedAt DESC")
    Page<PurchaseJpaEntity> findByRestaurantIdOrderByPurchasedAtDesc(@Param("restaurantId") UUID restaurantId, Pageable pageable);

    @EntityGraph(attributePaths = {"supplier"})
    Optional<PurchaseJpaEntity> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    long countByRestaurantIdAndStatusAndPurchasedAtGreaterThanEqual(UUID restaurantId, PurchaseStatus status, Instant since);

    @Query("SELECT COALESCE(SUM(p.total), 0) FROM PurchaseJpaEntity p WHERE p.restaurantId = :restaurantId AND p.status = 'ACTIVE' AND p.purchasedAt >= :since")
    BigDecimal sumTotalByRestaurantIdAndPurchasedAtSince(
            @Param("restaurantId") UUID restaurantId,
            @Param("since") Instant since);
}
