package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.PermissionOverrideJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface JpaPermissionOverrideRepository extends JpaRepository<PermissionOverrideJpaEntity, UUID> {

    List<PermissionOverrideJpaEntity> findByUserIdAndRestaurantId(UUID userId, UUID restaurantId);

    @Modifying
    @Query("DELETE FROM PermissionOverrideJpaEntity p WHERE p.userId = :userId AND p.restaurantId = :restaurantId")
    void deleteByUserIdAndRestaurantId(@Param("userId") UUID userId, @Param("restaurantId") UUID restaurantId);
}
