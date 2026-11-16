package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.purchase.Purchase;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseRepository {

    Page<Purchase> findByRestaurantIdOrderByPurchasedAtDesc(UUID restaurantId, PageRequest pageRequest);

    Optional<Purchase> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    Purchase save(Purchase purchase);

    /** Las compras no revertidas del período, con su proveedor, de la más vieja a la más nueva. */
    List<Purchase> findActiveByRestaurantIdAndPurchasedAtBetween(UUID restaurantId, Instant from, Instant to);

    long countByRestaurantIdAndPurchasedAtGreaterThanEqual(UUID restaurantId, Instant since);

    BigDecimal sumTotalByRestaurantIdAndPurchasedAtSince(UUID restaurantId, Instant since);

    long countByRestaurantIdAndPurchasedAtBetween(UUID restaurantId, Instant from, Instant to);

    BigDecimal sumTotalByRestaurantIdAndPurchasedAtBetween(UUID restaurantId, Instant from, Instant to);
}
