package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.history.PriceHistoryEntry;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Serie temporal de un dato maestro. Se escribe en la misma transacción que el cambio, a
 * diferencia del audit log, que es async y best-effort — ver el comentario de V22 para el porqué.
 *
 * <p>Las lecturas van sobre el índice {@code (entity_id, valid_from DESC)} que crea V22: el
 * vigente en una fecha es el último tramo que arrancó antes o en ese momento.
 */
public interface PriceHistoryRepository {

    PriceHistoryEntry saveMenuItemPrice(PriceHistoryEntry entry);

    PriceHistoryEntry saveProductYield(PriceHistoryEntry entry);

    /** El precio que regía en {@code at}, o vacío si el plato no existía todavía. */
    Optional<PriceHistoryEntry> findMenuItemPriceAt(UUID menuItemId, Instant at);

    /** El rendimiento que regía en {@code at}, o vacío si el insumo no existía todavía. */
    Optional<PriceHistoryEntry> findProductYieldAt(UUID productId, Instant at);

    /** La serie completa de precios de un plato, del cambio más viejo al más nuevo. */
    List<PriceHistoryEntry> findMenuItemPriceHistory(UUID menuItemId);

    /** La serie completa de rendimientos de un insumo, del cambio más viejo al más nuevo. */
    List<PriceHistoryEntry> findProductYieldHistory(UUID productId);
}
