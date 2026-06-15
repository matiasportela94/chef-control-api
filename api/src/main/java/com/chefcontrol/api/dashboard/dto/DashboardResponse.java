package com.chefcontrol.api.dashboard.dto;

import com.chefcontrol.application.service.DashboardService.DashboardSummary;
import com.chefcontrol.application.service.DashboardService.KpiSummary;

import java.math.BigDecimal;

public record DashboardResponse(Kpis kpis) {

    public static DashboardResponse from(DashboardSummary summary) {
        return new DashboardResponse(Kpis.from(summary.kpis()));
    }

    public record Kpis(
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
            // Mes anterior
            long       purchasesLastMonth,
            BigDecimal purchasesTotalLastMonth,
            long       salesCountLastMonth,
            BigDecimal salesTotalLastMonth,
            long       wasteEventsLastMonth,
            BigDecimal wasteTotalArsLastMonth,
            BigDecimal salesCostLastMonth
    ) {
        public static Kpis from(KpiSummary kpis) {
            return new Kpis(
                    kpis.totalActiveProducts(),
                    kpis.lowStockProducts(),
                    kpis.overstockProducts(),
                    kpis.purchasesThisMonth(),
                    kpis.purchasesTotalThisMonth(),
                    kpis.salesCountThisMonth(),
                    kpis.salesTotalThisMonth(),
                    kpis.wasteEventsThisMonth(),
                    kpis.wasteTotalArsThisMonth(),
                    kpis.salesCostThisMonth(),
                    kpis.purchasesLastMonth(),
                    kpis.purchasesTotalLastMonth(),
                    kpis.salesCountLastMonth(),
                    kpis.salesTotalLastMonth(),
                    kpis.wasteEventsLastMonth(),
                    kpis.wasteTotalArsLastMonth(),
                    kpis.salesCostLastMonth());
        }
    }
}
