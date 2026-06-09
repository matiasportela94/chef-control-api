package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.ProductCategoryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaProductCategoryRepository extends JpaRepository<ProductCategoryJpaEntity, UUID> {

    @Query("SELECT c FROM ProductCategoryJpaEntity c WHERE c.isSystem = true OR c.restaurantId = :restaurantId ORDER BY c.isSystem DESC, c.name ASC")
    List<ProductCategoryJpaEntity> findAllAccessibleToRestaurant(@Param("restaurantId") UUID restaurantId);

    Optional<ProductCategoryJpaEntity> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    @Query("SELECT c FROM ProductCategoryJpaEntity c WHERE c.id = :id AND (c.isSystem = true OR c.restaurantId = :restaurantId)")
    Optional<ProductCategoryJpaEntity> findByIdAccessibleTo(@Param("id") UUID id, @Param("restaurantId") UUID restaurantId);
}
