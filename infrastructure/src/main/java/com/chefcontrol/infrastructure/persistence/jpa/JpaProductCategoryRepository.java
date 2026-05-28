package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.ProductCategoryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaProductCategoryRepository extends JpaRepository<ProductCategoryJpaEntity, UUID> {

    List<ProductCategoryJpaEntity> findAllByRestaurantIdOrderByName(UUID restaurantId);

    Optional<ProductCategoryJpaEntity> findByIdAndRestaurantId(UUID id, UUID restaurantId);
}
