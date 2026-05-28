package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.MenuItemJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaMenuItemRepository extends JpaRepository<MenuItemJpaEntity, UUID> {

    Page<MenuItemJpaEntity> findByRestaurantIdAndActiveTrue(UUID restaurantId, Pageable pageable);

    Optional<MenuItemJpaEntity> findByIdAndRestaurantId(UUID id, UUID restaurantId);
}
