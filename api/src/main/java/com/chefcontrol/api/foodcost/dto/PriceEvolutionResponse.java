package com.chefcontrol.api.foodcost.dto;

import com.chefcontrol.application.service.FoodCostService.PriceEvolutionPoint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * La evolución de un plato: precio de venta, costo de la receta y food cost en cada momento en
 * que el precio cambió.
 *
 * <p>{@code seriesReliableFrom} es la fecha desde la que las series son verdad. Antes de eso,
 * V22/V23 sembraron un punto con el valor que había el día del backfill, así que los números
 * anteriores describen el presente proyectado hacia atrás. La pantalla tiene que decirlo.
 */
public record PriceEvolutionResponse(
        UUID menuItemId,
        String menuItemName,
        Instant from,
        Instant to,
        Instant seriesReliableFrom,
        List<EvolutionPoint> points
) {
    /**
     * Nombre propio y no "Point": el nombre del record es el del modelo generado en el cliente,
     * y un `Point` suelto en src/app/api/models no dice de qué es punto.
     */
    public record EvolutionPoint(
            Instant at,
            BigDecimal menuPrice,
            BigDecimal costPerServing,
            BigDecimal foodCostPercentage
    ) {
        static EvolutionPoint from(PriceEvolutionPoint p) {
            return new EvolutionPoint(p.at(), p.menuPrice(), p.costPerServing(), p.foodCostPercentage());
        }
    }

    public static PriceEvolutionResponse from(UUID menuItemId, String menuItemName,
                                              Instant from, Instant to,
                                              Instant seriesReliableFrom,
                                              List<PriceEvolutionPoint> points) {
        return new PriceEvolutionResponse(menuItemId, menuItemName, from, to, seriesReliableFrom,
                points.stream().map(EvolutionPoint::from).toList());
    }
}
