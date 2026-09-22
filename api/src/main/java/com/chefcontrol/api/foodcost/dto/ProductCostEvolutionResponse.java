package com.chefcontrol.api.foodcost.dto;

import com.chefcontrol.application.service.FoodCostService.ProductCostPoint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cómo se movió el costo de un insumo: lo que se pagó por unidad y lo que cuesta la unidad que
 * llega al plato, una vez aplicado el rendimiento.
 *
 * <p>{@code yieldReliableFrom} solo acota la serie del <b>rendimiento</b>: el costo de compra sale
 * de {@code stock_movements}, que es inmutable y está completo desde el día uno, así que esa mitad
 * no tiene el agujero del backfill.
 */
public record ProductCostEvolutionResponse(
        UUID productId,
        String productName,
        String unitAbbreviation,
        Instant from,
        Instant to,
        Instant yieldReliableFrom,
        List<CostPoint> points
) {
    public record CostPoint(
            Instant at,
            BigDecimal purchaseCost,
            BigDecimal yieldPercentage,
            BigDecimal usableCost
    ) {
        static CostPoint from(ProductCostPoint p) {
            return new CostPoint(p.at(), p.purchaseCost(), p.yieldPercentage(), p.usableCost());
        }
    }

    public static ProductCostEvolutionResponse from(UUID productId, String productName,
                                                    String unitAbbreviation,
                                                    Instant from, Instant to,
                                                    Instant yieldReliableFrom,
                                                    List<ProductCostPoint> points) {
        return new ProductCostEvolutionResponse(productId, productName, unitAbbreviation,
                from, to, yieldReliableFrom, points.stream().map(CostPoint::from).toList());
    }
}
