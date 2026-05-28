package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.sale.Sale;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SaleRepository {

    Page<Sale> findByRestaurantIdOrderBySoldAtDesc(UUID restaurantId, PageRequest pageRequest);

    Optional<Sale> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    Sale save(Sale sale);

    BigDecimal sumTotalAmountByRestaurantIdAndSoldAtBetween(UUID restaurantId, Instant from, Instant to);
}
