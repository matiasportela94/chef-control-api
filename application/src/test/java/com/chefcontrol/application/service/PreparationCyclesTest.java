package com.chefcontrol.application.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PreparationCyclesTest {

    private static Optional<List<String>> cycle(String start, Map<String, List<String>> recipes) {
        return PreparationCycles.findCycle(start, k -> recipes.getOrDefault(k, List.of()));
    }

    @Test
    void aPreparationThatEndsUpUsingItselfIsACycleWithItsPath() {
        var recipes = Map.of("mil islas", List.of("mayonesa", "ketchup"), "mayonesa", List.of("aceite", "mil islas"));

        assertEquals(Optional.of(List.of("mil islas", "mayonesa", "mil islas")), cycle("mil islas", recipes));
        assertEquals(Optional.of(List.of("salsa", "salsa")), cycle("salsa", Map.of("salsa", List.of("salsa"))));
    }

    @Test
    void sharingAnIngredientIsNotACycle() {
        // mil islas y tártara llevan mayonesa: un diamante, no un ciclo
        var recipes = Map.of("combo", List.of("mil islas", "tartara"), "mil islas", List.of("mayonesa"),
                "tartara", List.of("mayonesa"), "mayonesa", List.of("aceite", "huevo"));

        assertEquals(Optional.empty(), cycle("combo", recipes));
    }
}
