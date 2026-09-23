package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.RestaurantJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaRestaurantRepository extends JpaRepository<RestaurantJpaEntity, UUID> {

    Optional<RestaurantJpaEntity> findByIdAndIsActiveTrue(UUID id);

    boolean existsBySlug(String slug);

    List<RestaurantJpaEntity> findAllByAccountId(UUID accountId);
}
