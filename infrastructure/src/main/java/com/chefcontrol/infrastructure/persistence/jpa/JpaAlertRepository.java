package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.domain.alert.AlertType;
import com.chefcontrol.infrastructure.persistence.entity.AlertJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface JpaAlertRepository extends JpaRepository<AlertJpaEntity, UUID> {

    Page<AlertJpaEntity> findByRestaurantIdAndResolvedAtIsNullOrderByCreatedAtDesc(UUID restaurantId, Pageable pageable);

    Optional<AlertJpaEntity> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    Optional<AlertJpaEntity> findByProductIdAndTypeAndResolvedAtIsNull(UUID productId, AlertType type);

    @Modifying
    @Query("UPDATE AlertJpaEntity a SET a.resolvedAt = :now WHERE a.productId = :productId AND a.type = :type AND a.resolvedAt IS NULL")
    void resolveByProductAndType(@Param("productId") UUID productId,
                                 @Param("type") AlertType type,
                                 @Param("now") Instant now);
}
