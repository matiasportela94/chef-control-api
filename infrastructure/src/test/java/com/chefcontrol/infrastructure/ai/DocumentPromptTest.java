package com.chefcontrol.infrastructure.ai;

import com.chefcontrol.application.port.AIService.DocumentKind;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class DocumentPromptTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void aRecipePhotoIsNumberedByTheBackAndKeepsItsServingsUndivided() throws Exception {
        var reading = DocumentPrompt.parse(mapper.readTree("""
                {"kind":"recipe","products":[{"name":"Algo","unit":"kg"}],
                 "dishes":[{"menuItem":"Ñoquis","servings":4,"ingredients":[
                    {"ingredient":"papas","quantity":1,"unit":"kg","catalogName":"Papa"},
                    {"ingredient":"nuez moscada","quantity":2,"unit":"g"}]}],
                 "preparations":[{"preparation":"Fileto","yieldQuantity":1.5,"yieldUnit":"l","ingredients":[
                    {"ingredient":"tomate","quantity":2,"unit":"kg"}]}],
                 "proposedProducts":[{"name":"Nuez moscada","unit":"kg"}],"warnings":["no leí la sal"]}
                """));

        assertEquals(DocumentKind.RECIPE, reading.kind());
        var r = reading.recipes();
        assertTrue(r.products().isEmpty());                                   // una foto no da de alta insumos sueltos
        assertEquals(1, r.recipes().get(0).row());
        assertEquals(2, r.recipes().get(1).row());
        assertEquals(3, r.preparations().getFirst().row());
        assertEquals(new BigDecimal("1"), r.recipes().get(0).quantity());     // sin dividir: lo divide el servicio
        assertEquals(new BigDecimal("4"), r.dishServings().getFirst().servings());
        assertEquals(2, r.proposedProducts().getFirst().row());               // la fila del ingrediente que lo usa
        assertEquals("Papa", r.ingredientMatches().getFirst().catalogName());
    }
}
