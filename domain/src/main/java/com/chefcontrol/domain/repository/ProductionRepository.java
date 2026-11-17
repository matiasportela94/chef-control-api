package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.domain.stock.Production;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductionRepository {
    Production save(Production production);

    Optional<Production> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    /** Las automáticas de una venta, la más nueva primero (el orden en que se revierten). */
    List<Production> findBySaleItemIdNewestFirst(UUID saleItemId);

    /** Las automáticas que hizo otra producción, la más nueva primero. */
    List<Production> findByParentProductionIdNewestFirst(UUID parentProductionId);

    /** Filtros opcionales (null = todos); la más reciente primero. */
    Page<Production> findByRestaurant(UUID restaurantId, UUID productId, Boolean automatic,
                                      Instant from, Instant to, PageRequest pageRequest);
}
