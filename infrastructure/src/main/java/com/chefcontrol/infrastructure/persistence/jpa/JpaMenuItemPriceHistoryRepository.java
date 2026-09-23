package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.MenuItemPriceHistoryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaMenuItemPriceHistoryRepository extends JpaRepository<MenuItemPriceHistoryJpaEntity, UUID> {

    /** El tramo vigente en {@code at}: el último que arrancó antes o en ese momento. */
    Optional<MenuItemPriceHistoryJpaEntity> findFirstByMenuItemIdAndValidFromLessThanEqualOrderByValidFromDesc(
            UUID menuItemId, Instant at);

    List<MenuItemPriceHistoryJpaEntity> findByMenuItemIdOrderByValidFromAsc(UUID menuItemId);
}
