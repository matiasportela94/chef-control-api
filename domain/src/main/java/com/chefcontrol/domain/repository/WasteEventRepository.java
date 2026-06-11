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

    long countByRestaurantIdAndCreatedAtGreaterThanEqual(UUID restaurantId, Instant since);

    BigDecimal sumCostByRestaurantIdAndCreatedAtBetween(UUID restaurantId, Instant from, Instant to);
}
