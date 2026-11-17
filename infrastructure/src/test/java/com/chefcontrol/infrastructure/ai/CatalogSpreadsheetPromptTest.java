package com.chefcontrol.infrastructure.ai;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AIService.CatalogSpreadsheetRequest;
import com.chefcontrol.application.port.AIService.IngredientMatch;
import com.chefcontrol.application.service.CatalogImportService.RecipeRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CatalogSpreadsheetPromptTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void cellsAreWrittenSoTheRowFormatSurvives() {
        assertEquals("10000000", CatalogSpreadsheetPrompt.cell(1.0E7));
        assertEquals("0.05", CatalogSpreadsheetPrompt.cell(0.05));
        assertEquals("3", CatalogSpreadsheetPrompt.cell(3));
        assertEquals("", CatalogSpreadsheetPrompt.cell(null));
        assertEquals("Papa / Batata lavada", CatalogSpreadsheetPrompt.cell(" Papa | Batata\nlavada "));
    }

    @Test
    void dishesAreFlattenedToOneRowPerIngredientWithPriceAndSectionOnTheFirst() throws Exception {
        var result = CatalogSpreadsheetPrompt.parse(mapper.readTree("""
                {"products":[{"row":3,"name":" Papa ","unit":"kg","minStock":"10,5"}],
                 "dishes":[{"menuItem":"Ñoquis","price":9500,"section":"Pastas","ingredients":[
                    {"row":12,"ingredient":"Papas","quantity":0.25,"unit":"kg","catalogName":"Papa"},
                    {"row":13,"ingredient":"Harina 0000","quantity":"mucha","unit":"g"}]}],
                 "proposedProducts":[{"row":12,"name":"Cebolla","unit":"kg"}],
                 "warnings":["ignoré la fila 9 (total)"]}
                """));

        assertEquals("Papa", result.products().getFirst().name());
        assertEquals(new BigDecimal("10.5"), result.products().getFirst().minStock());
        assertEquals(List.of(
                new RecipeRow(12, "Ñoquis", new BigDecimal("9500"), "Pastas", "Papas", new BigDecimal("0.25"), "kg"),
                // lo ilegible queda vacío y el import lo marca en su fila
                new RecipeRow(13, "Ñoquis", null, null, "Harina 0000", null, "g")),
                result.recipes());
        assertEquals(List.of("ignoré la fila 9 (total)"), result.warnings());
        assertEquals("Cebolla", result.proposedProducts().getFirst().name());
        assertEquals(12, result.proposedProducts().getFirst().row());
        assertEquals(List.of(new IngredientMatch(12, "Papas", "Papa")), result.ingredientMatches());
    }

    /** El ref es un número global del front, no la fila del Excel: citarlo en un aviso manda al usuario a otra fila. */
    @Test
    void warningsNeverCiteRowsOrRefs() {
        String prompt = CatalogSpreadsheetPrompt.systemPrompt(
                new CatalogSpreadsheetRequest(List.of(), List.of(), List.of(), List.of(), List.of()), "");

        assertTrue(prompt.contains("nunca números de fila ni refs"), "falta la regla de los warnings");
        assertFalse(prompt.contains("con sus refs"));
        assertFalse(prompt.contains("cuál (ref)"));
    }

    @Test
    void jsonIsFoundInsideAFencedAnswer() {
        var json = CatalogSpreadsheetPrompt.extractJson("```json\n{\"products\":[],\"dishes\":[],\"warnings\":[]}\n```", mapper);
        assertTrue(json.has("dishes"));
    }

    @Test
    void anAnswerWithoutJsonIsAFailure() {
        var e = assertThrows(AppException.class, () -> CatalogSpreadsheetPrompt.extractJson("No puedo ayudarte", mapper));
        assertEquals(ErrorCode.AI_INTERPRETATION_FAILED, e.getErrorCode());
    }
}
