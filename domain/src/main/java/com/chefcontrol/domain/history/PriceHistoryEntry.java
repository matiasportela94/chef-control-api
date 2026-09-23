package com.chefcontrol.domain.history;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Un punto de una serie temporal de un dato maestro: "este valor rigió desde este momento".
 *
 * <p>No lleva {@code validUntil}: el final de cada tramo es el {@code validFrom} del punto
 * siguiente. Guardarlo obligaría a actualizar dos filas por cambio, que es donde estas series
 * se desincronizan.
 *
 * <p>Sirve para las dos tablas —precio de plato y rendimiento de insumo— porque el dato es el
 * mismo: una entidad, un número y desde cuándo rige.
 */
public record PriceHistoryEntry(
        UUID id,
        UUID restaurantId,
        UUID entityId,
        BigDecimal value,
        Instant validFrom,
        UUID changedBy
) {
    public static PriceHistoryEntry of(UUID restaurantId, UUID entityId, BigDecimal value, UUID changedBy) {
        return new PriceHistoryEntry(null, restaurantId, entityId, value, Instant.now(), changedBy);
    }
}
