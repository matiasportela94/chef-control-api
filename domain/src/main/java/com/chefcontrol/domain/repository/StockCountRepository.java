package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.domain.stock.StockCount;

import java.util.Optional;
import java.util.UUID;

public interface StockCountRepository {

    Page<StockCount> findByRestaurantIdOrderByCountedAtDesc(UUID restaurantId, PageRequest pageRequest);

    Optional<StockCount> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    StockCount save(StockCount count);

    /** De esos conteos, los que tenían ajustes y ya no les queda ninguno en pie. */
    java.util.Set<UUID> findReversedIds(java.util.Collection<UUID> ids);
}
