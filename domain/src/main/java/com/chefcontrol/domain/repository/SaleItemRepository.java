package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.sale.SaleItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SaleItemRepository {

    List<SaleItem> findBySaleId(UUID saleId);

    int countBySaleId(UUID saleId);

    SaleItem save(SaleItem saleItem);

    BigDecimal sumRevenueByMenuItemAndPeriod(UUID menuItemId, UUID restaurantId, Instant from, Instant to);

    int sumQuantitySoldByMenuItemAndPeriod(UUID menuItemId, UUID restaurantId, Instant from, Instant to);
}
