package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.domain.waste.WasteEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface WasteEventRepository {

    Page<WasteEvent> findByRestaurantIdOrderByCreatedAtDesc(UUID restaurantId, PageRequest pageRequest);

    Optional<WasteEvent> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    WasteEvent save(WasteEvent event);

    /** De esos eventos, los deshechos: su movimiento WASTE está revertido (el waste_event queda igual). */
    java.util.Set<UUID> findUndoneIds(java.util.Collection<UUID> ids);

    /** Las tres sin las mermas deshechas (como las ventas y compras revertidas): ver JpaWasteEventRepository. */
    long countByRestaurantIdAndCreatedAtGreaterThanEqual(UUID restaurantId, Instant since);

    long countByRestaurantIdAndCreatedAtBetween(UUID restaurantId, Instant from, Instant to);

    BigDecimal sumCostByRestaurantIdAndCreatedAtBetween(UUID restaurantId, Instant from, Instant to);
}
