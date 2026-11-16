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

    /** Sin las ventas revertidas, igual que la cantidad vendida. */
    BigDecimal sumRevenueByMenuItemAndPeriod(UUID menuItemId, UUID restaurantId, Instant from, Instant to);

    /** Ídem, sin el IVA de la venta (el importe entero en las anteriores al IVA). */
    BigDecimal sumNetRevenueByMenuItemAndPeriod(UUID menuItemId, UUID restaurantId, Instant from, Instant to);

    int sumQuantitySoldByMenuItemAndPeriod(UUID menuItemId, UUID restaurantId, Instant from, Instant to);
}
