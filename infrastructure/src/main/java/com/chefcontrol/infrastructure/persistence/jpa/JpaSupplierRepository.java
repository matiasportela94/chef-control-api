package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.SupplierJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaSupplierRepository extends JpaRepository<SupplierJpaEntity, UUID> {

    List<SupplierJpaEntity> findAllByRestaurantIdAndIsActiveTrueOrderByName(UUID restaurantId);

    Optional<SupplierJpaEntity> findByIdAndRestaurantId(UUID id, UUID restaurantId);
}
