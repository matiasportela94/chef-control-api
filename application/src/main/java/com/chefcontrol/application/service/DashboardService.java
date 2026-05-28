package com.chefcontrol.application.service;

import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.ProductStockProjection;
import com.chefcontrol.domain.repository.PurchaseRepository;
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

    private final ProductRepository productRepository;
    private final PurchaseRepository purchaseRepository;
    private final WasteEventRepository wasteEventRepository;

    @Transactional(readOnly = true)
    public DashboardSummary getSummary() {
        UUID restaurantId = TenantContext.require();
        Instant monthStart = firstDayOfCurrentMonth();

        List<ProductStockProjection> stockRows = productRepository.findProductStockByRestaurant(restaurantId);

        long lowStockCount = stockRows.stream().filter(this::isLowStock).count();
        long overstockCount = stockRows.stream().filter(this::isOverstock).count();

        long purchasesThisMonth = purchaseRepository.countByRestaurantIdAndPurchasedAtGreaterThanEqual(restaurantId, monthStart);
        BigDecimal purchasesTotalThisMonth = purchaseRepository.sumTotalByRestaurantIdAndPurchasedAtSince(restaurantId, monthStart);
        long wasteEventsThisMonth = wasteEventRepository.countByRestaurantIdAndCreatedAtGreaterThanEqual(restaurantId, monthStart);

        KpiSummary kpis = new KpiSummary(
                stockRows.size(),
                lowStockCount,
                overstockCount,
                purchasesThisMonth,
                purchasesTotalThisMonth,
                wasteEventsThisMonth);

        return new DashboardSummary(stockRows, kpis);
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

    public record DashboardSummary(
            List<ProductStockProjection> products,
            KpiSummary kpis
    ) {}

    public record KpiSummary(
            long totalActiveProducts,
            long lowStockProducts,
            long overstockProducts,
            long purchasesThisMonth,
            BigDecimal purchasesTotalThisMonth,
            long wasteEventsThisMonth
    ) {}
}
