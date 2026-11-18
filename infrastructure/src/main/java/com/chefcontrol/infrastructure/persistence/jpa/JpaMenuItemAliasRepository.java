package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.MenuItemAliasJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaMenuItemAliasRepository extends JpaRepository<MenuItemAliasJpaEntity, UUID> {

    List<MenuItemAliasJpaEntity> findByRestaurantId(UUID restaurantId);

    Optional<MenuItemAliasJpaEntity> findByRestaurantIdAndPrintedKey(UUID restaurantId, String printedKey);
}
