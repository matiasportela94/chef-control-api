package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.MenuSectionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaMenuSectionRepository extends JpaRepository<MenuSectionJpaEntity, UUID> {

    List<MenuSectionJpaEntity> findByRestaurantIdOrderBySortOrderAscNameAsc(UUID restaurantId);

    Optional<MenuSectionJpaEntity> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    boolean existsByRestaurantIdAndNameIgnoreCase(UUID restaurantId, String name);
}
