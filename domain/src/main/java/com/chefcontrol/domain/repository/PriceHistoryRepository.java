package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.history.PriceHistoryEntry;

/**
 * Serie temporal de un dato maestro. Se escribe en la misma transacción que el cambio, a
 * diferencia del audit log, que es async y best-effort — ver el comentario de V22 para el porqué.
 *
 * <p>Solo escritura por ahora: la tabla existe desde hoy porque el historial no se puede
 * backfillear, pero todavía no hay pantalla que lo lea. Las lecturas ("el valor vigente en tal
 * fecha", "la serie completa") son queries derivadas de Spring Data sobre el índice que ya crea
 * V22 — se agregan cuando F13 o C2 las necesiten, no antes.
 */
public interface PriceHistoryRepository {

    PriceHistoryEntry saveMenuItemPrice(PriceHistoryEntry entry);

    PriceHistoryEntry saveProductYield(PriceHistoryEntry entry);
}
