package com.chefcontrol.api.dashboard.dto;

import com.chefcontrol.application.service.DashboardService.DashboardSummary;
import com.chefcontrol.application.service.DashboardService.KpiSummary;
import com.chefcontrol.domain.repository.ProductStockProjection;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DashboardResponse(
        List<ProductStock> products,
        Kpis kpis
) {
    public static DashboardResponse from(DashboardSummary summary) {
        return new DashboardResponse(
                summary.products().stream().map(ProductStock::from).toList(),
                Kpis.from(summary.kpis()));
    }

    public record ProductStock(
            UUID productId,
            String name,
            String sku,
            String unitAbbreviation,
            BigDecimal currentStock,
            BigDecimal minStock,
            BigDecimal maxStock,
            StockStatus status
    ) {
        public static ProductStock from(ProductStockProjection p) {
            return new ProductStock(
                    p.getId(),
                    p.getName(),
                    p.getSku(),
                    p.getUnitAbbreviation(),
                    p.getCurrentStock(),
                    p.getMinStock(),
                    p.getMaxStock(),
                    computeStatus(p));
        }

        private static StockStatus computeStatus(ProductStockProjection p) {
            if (p.getMinStock() == null && p.getMaxStock() == null) return StockStatus.NO_THRESHOLD;
            if (p.getMinStock() != null && p.getCurrentStock().compareTo(p.getMinStock()) < 0) return StockStatus.LOW_STOCK;
            if (p.getMaxStock() != null && p.getCurrentStock().compareTo(p.getMaxStock()) > 0) return StockStatus.OVERSTOCK;
            return StockStatus.OK;
        }
    }

    public enum StockStatus {
        OK, LOW_STOCK, OVERSTOCK, NO_THRESHOLD
    }

    public record Kpis(
            long totalActiveProducts,
            long lowStockProducts,
            long overstockProducts,
            long purchasesThisMonth,
            BigDecimal purchasesTotalThisMonth,
            long wasteEventsThisMonth
    ) {
        public static Kpis from(KpiSummary kpis) {
            return new Kpis(
                    kpis.totalActiveProducts(),
                    kpis.lowStockProducts(),
                    kpis.overstockProducts(),
                    kpis.purchasesThisMonth(),
                    kpis.purchasesTotalThisMonth(),
                    kpis.wasteEventsThisMonth());
        }
    }
}
