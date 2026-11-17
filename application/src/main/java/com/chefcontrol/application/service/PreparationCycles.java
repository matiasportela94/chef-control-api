package com.chefcontrol.application.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Una preparación no puede llevarse a sí misma, ni directo ni a través de otras (mil islas → mayonesa base → mil islas):
 * producirla no terminaría nunca. Lo usan el alta de la receta (con ids) y el import (con nombres de la planilla).
 */
final class PreparationCycles {

    private PreparationCycles() {}

    /**
     * El camino desde {@code start} hasta volver a él, si las recetas lo forman (por ejemplo [A, B, A]); vacío si no.
     *
     * @param ingredientsOf los ingredientes de una preparación; vacío si el nodo no es preparación
     */
    static <K> Optional<List<K>> findCycle(K start, Function<K, Collection<K>> ingredientsOf) {
        List<K> path = new ArrayList<>();
        path.add(start);
        return walk(start, start, ingredientsOf, path, new ArrayList<>());
    }

    private static <K> Optional<List<K>> walk(K start, K node, Function<K, Collection<K>> ingredientsOf,
                                              List<K> path, List<K> done) {
        for (K next : ingredientsOf.apply(node)) {
            if (next.equals(start)) {
                List<K> cycle = new ArrayList<>(path);
                cycle.add(start);
                return Optional.of(cycle);
            }
            if (path.contains(next) || done.contains(next)) continue;
            path.add(next);
            Optional<List<K>> found = walk(start, next, ingredientsOf, path, done);
            if (found.isPresent()) return found;
            path.removeLast();
            done.add(next);
        }
        return Optional.empty();
    }
}
