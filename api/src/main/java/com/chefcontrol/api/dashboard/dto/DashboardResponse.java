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
            /** Costo de lo vendido / ingreso sin IVA, en %. Null si no hubo ventas. */
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
                    kpis.foodCostPercentageThisMonth(),
                    kpis.purchasesLastMonth(),
                    kpis.purchasesTotalLastMonth(),
                    kpis.salesCountLastMonth(),
                    kpis.salesTotalLastMonth(),
                    kpis.wasteEventsLastMonth(),
                    kpis.wasteTotalArsLastMonth(),
                    kpis.salesCostLastMonth(),
                    kpis.foodCostPercentageLastMonth());
        }
    }
}
