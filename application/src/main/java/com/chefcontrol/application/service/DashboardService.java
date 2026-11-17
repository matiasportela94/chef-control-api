package com.chefcontrol.application.service;

import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.finance.FoodCostMetric;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.ProductStockProjection;
import com.chefcontrol.domain.repository.PurchaseRepository;
import com.chefcontrol.domain.repository.SaleRepository;
import com.chefcontrol.domain.repository.StockMovementRepository;
import com.chefcontrol.domain.repository.WasteEventRepository;
import com.chefcontrol.domain.shared.time.ChefControlTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
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
        UUID    restaurantId   = TenantContext.require();
        Instant now            = Instant.now();
        Instant monthStart     = monthStart(now, 0);
        Instant prevMonthStart = monthStart(now, 1);
        Instant prevMonthEnd   = monthStart;

        List<ProductStockProjection> stockRows = productRepository.findProductStockByRestaurant(restaurantId);

        long lowStockCount  = stockRows.stream().filter(this::isLowStock).count();
        long overstockCount = stockRows.stream().filter(this::isOverstock).count();

        // — Mes actual —
        long       purchasesThisMonth      = purchaseRepository.countByRestaurantIdAndPurchasedAtGreaterThanEqual(restaurantId, monthStart);
        BigDecimal purchasesTotalThisMonth = purchaseRepository.sumTotalByRestaurantIdAndPurchasedAtSince(restaurantId, monthStart);

        long       salesCountThisMonth     = saleRepository.countByRestaurantIdAndSoldAtBetween(restaurantId, monthStart, now);
        BigDecimal salesTotalThisMonth     = saleRepository.sumTotalAmountByRestaurantIdAndSoldAtBetween(restaurantId, monthStart, now);

        long       wasteEventsThisMonth    = wasteEventRepository.countByRestaurantIdAndCreatedAtGreaterThanEqual(restaurantId, monthStart);
        BigDecimal wasteTotalArsThisMonth  = wasteEventRepository.sumCostByRestaurantIdAndCreatedAtBetween(restaurantId, monthStart, now);

        BigDecimal salesCostThisMonth      = stockMovementRepository.sumSalesCost(restaurantId, monthStart, now);
        BigDecimal foodCostThisMonth       = foodCostPercentage(
                saleRepository.sumNetRevenueByRestaurantIdAndSoldAtBetween(restaurantId, monthStart, now), salesCostThisMonth);

        // — Mes anterior —
        long       purchasesLastMonth      = purchaseRepository.countByRestaurantIdAndPurchasedAtBetween(restaurantId, prevMonthStart, prevMonthEnd);
        BigDecimal purchasesTotalLastMonth = purchaseRepository.sumTotalByRestaurantIdAndPurchasedAtBetween(restaurantId, prevMonthStart, prevMonthEnd);

        long       salesCountLastMonth     = saleRepository.countByRestaurantIdAndSoldAtBetween(restaurantId, prevMonthStart, prevMonthEnd);
        BigDecimal salesTotalLastMonth     = saleRepository.sumTotalAmountByRestaurantIdAndSoldAtBetween(restaurantId, prevMonthStart, prevMonthEnd);

        long       wasteEventsLastMonth    = wasteEventRepository.countByRestaurantIdAndCreatedAtBetween(restaurantId, prevMonthStart, prevMonthEnd);
        BigDecimal wasteTotalArsLastMonth  = wasteEventRepository.sumCostByRestaurantIdAndCreatedAtBetween(restaurantId, prevMonthStart, prevMonthEnd);

        BigDecimal salesCostLastMonth      = stockMovementRepository.sumSalesCost(restaurantId, prevMonthStart, prevMonthEnd);
        BigDecimal foodCostLastMonth       = foodCostPercentage(
                saleRepository.sumNetRevenueByRestaurantIdAndSoldAtBetween(restaurantId, prevMonthStart, prevMonthEnd), salesCostLastMonth);

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
                salesCostThisMonth,
                foodCostThisMonth,
                purchasesLastMonth,
                purchasesTotalLastMonth,
                salesCountLastMonth,
                salesTotalLastMonth,
                wasteEventsLastMonth,
                wasteTotalArsLastMonth,
                salesCostLastMonth,
                foodCostLastMonth);

        return new DashboardSummary(kpis);
    }

    /** Sobre el ingreso sin IVA, como {@code /food-cost}. Null sin ventas: no hay food cost, no es 0%. */
    private static BigDecimal foodCostPercentage(BigDecimal netRevenue, BigDecimal salesCost) {
        return netRevenue.signum() == 0 ? null : new FoodCostMetric(netRevenue, salesCost).percentage();
    }

    private boolean isLowStock(ProductStockProjection row) {
        return row.getMinStock() != null
                && row.getCurrentStock().compareTo(row.getMinStock()) < 0;
    }

    private boolean isOverstock(ProductStockProjection row) {
        return row.getMaxStock() != null
                && row.getCurrentStock().compareTo(row.getMaxStock()) > 0;
    }

    /**
     * El día 1 a las 00:00 en Argentina, {@code monthsBack} meses atrás. En UTC el mes empezaba a las 21 h del último
     * día de acá: esas tres horas de ventas caían en el mes siguiente, y la noche del último día "este mes" salía vacío.
     */
    static Instant monthStart(Instant now, int monthsBack) {
        return now.atZone(ChefControlTime.DEFAULT_ZONE).toLocalDate().withDayOfMonth(1).minusMonths(monthsBack)
                .atStartOfDay(ChefControlTime.DEFAULT_ZONE).toInstant();
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
            BigDecimal salesCostThisMonth,
            BigDecimal foodCostPercentageThisMonth,
            // Mes anterior
            long       purchasesLastMonth,
            BigDecimal purchasesTotalLastMonth,
            long       salesCountLastMonth,
            BigDecimal salesTotalLastMonth,
            long       wasteEventsLastMonth,
            BigDecimal wasteTotalArsLastMonth,
            BigDecimal salesCostLastMonth,
            BigDecimal foodCostPercentageLastMonth
    ) {}
}
