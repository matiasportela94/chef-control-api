package com.chefcontrol.application.service;

import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.ProductStockProjection;
import com.chefcontrol.domain.repository.PurchaseRepository;
import com.chefcontrol.domain.repository.SaleRepository;
import com.chefcontrol.domain.repository.StockMovementRepository;
import com.chefcontrol.domain.repository.WasteEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProductRepository        productRepository;
    private final PurchaseRepository       purchaseRepository;
    private final SaleRepository           saleRepository;
    private final WasteEventRepository     wasteEventRepository;
    private final StockMovementRepository  stockMovementRepository;

    @Transactional(readOnly = true)
    public DashboardSummary getSummary() {
        UUID    restaurantId = TenantContext.require();
        Instant monthStart   = firstDayOfCurrentMonth();
        Instant now          = Instant.now();

        List<ProductStockProjection> stockRows = productRepository.findProductStockByRestaurant(restaurantId);

        long lowStockCount  = stockRows.stream().filter(this::isLowStock).count();
        long overstockCount = stockRows.stream().filter(this::isOverstock).count();

        long       purchasesThisMonth      = purchaseRepository.countByRestaurantIdAndPurchasedAtGreaterThanEqual(restaurantId, monthStart);
        BigDecimal purchasesTotalThisMonth = purchaseRepository.sumTotalByRestaurantIdAndPurchasedAtSince(restaurantId, monthStart);

        long       salesCountThisMonth     = saleRepository.countByRestaurantIdAndSoldAtBetween(restaurantId, monthStart, now);
        BigDecimal salesTotalThisMonth     = saleRepository.sumTotalAmountByRestaurantIdAndSoldAtBetween(restaurantId, monthStart, now);

        long       wasteEventsThisMonth    = wasteEventRepository.countByRestaurantIdAndCreatedAtGreaterThanEqual(restaurantId, monthStart);
        BigDecimal wasteTotalArsThisMonth  = wasteEventRepository.sumCostByRestaurantIdAndCreatedAtBetween(restaurantId, monthStart, now);

        BigDecimal salesCostThisMonth      = stockMovementRepository.sumSalesCost(restaurantId, monthStart, now);

        KpiSummary kpis = new KpiSummary(
                stockRows.size(),
                lowStockCount,
                overstockCount,
                purchasesThisMonth,
                purchasesTotalThisMonth,
                salesCountThisMonth,
                salesTotalThisMonth,
                wasteEventsThisMonth,
                wasteTotalArsThisMonth,
                salesCostThisMonth);

        return new DashboardSummary(kpis);
    }

    private boolean isLowStock(ProductStockProjection row) {
        return row.getMinStock() != null
                && row.getCurrentStock().compareTo(row.getMinStock()) < 0;
    }

    private boolean isOverstock(ProductStockProjection row) {
        return row.getMaxStock() != null
                && row.getCurrentStock().compareTo(row.getMaxStock()) > 0;
    }

    private Instant firstDayOfCurrentMonth() {
        return Instant.now()
                .atOffset(ZoneOffset.UTC)
                .with(TemporalAdjusters.firstDayOfMonth())
                .toLocalDate()
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
    }

    // ── Result types ──────────────────────────────────────────────────────────

    public record DashboardSummary(KpiSummary kpis) {}

    public record KpiSummary(
            long       totalActiveProducts,
            long       lowStockProducts,
            long       overstockProducts,
            long       purchasesThisMonth,
            BigDecimal purchasesTotalThisMonth,
            long       salesCountThisMonth,
            BigDecimal salesTotalThisMonth,
            long       wasteEventsThisMonth,
            BigDecimal wasteTotalArsThisMonth,
            BigDecimal salesCostThisMonth
    ) {}
}
