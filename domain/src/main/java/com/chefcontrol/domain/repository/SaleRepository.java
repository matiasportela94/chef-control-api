package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.sale.Sale;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaleRepository {

    Page<Sale> findByRestaurantIdOrderBySoldAtDesc(UUID restaurantId, PageRequest pageRequest);

    Optional<Sale> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    Sale save(Sale sale);

    /** Sin las revertidas, como todo lo que suma o cuenta ventas. */
    BigDecimal sumTotalAmountByRestaurantIdAndSoldAtBetween(UUID restaurantId, Instant from, Instant to);

    /** El ingreso sin el IVA de las ventas: el neto, o el total de las anteriores al IVA. Sin las revertidas. */
    BigDecimal sumNetRevenueByRestaurantIdAndSoldAtBetween(UUID restaurantId, Instant from, Instant to);

    /** Las no revertidas del período, de la más vieja a la más nueva. */
    List<Sale> findActiveByRestaurantIdAndSoldAtBetween(UUID restaurantId, Instant from, Instant to);

    long countByRestaurantIdAndSoldAtBetween(UUID restaurantId, Instant from, Instant to);
}
