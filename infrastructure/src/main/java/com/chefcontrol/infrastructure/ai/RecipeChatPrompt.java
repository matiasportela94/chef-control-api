package com.chefcontrol.infrastructure.ai;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AIService.CatalogSpreadsheetInterpretation;
import com.chefcontrol.application.port.AIService.IngredientMatch;
import com.chefcontrol.application.port.AIService.RecipeLine;
import com.chefcontrol.application.port.AIService.RecipeMessageRequest;
import com.chefcontrol.application.service.CatalogImportService.PreparationRow;
import com.chefcontrol.application.service.CatalogImportService.ProductRow;
import com.chefcontrol.application.service.CatalogImportService.RecipeRow;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static com.chefcontrol.infrastructure.ai.CatalogSpreadsheetPrompt.number;
import static com.chefcontrol.infrastructure.ai.CatalogSpreadsheetPrompt.text;

/**
 * La segunda llamada de un mensaje de receta del chat, igual para Claude y Gemini. Recibe la receta actual y devuelve la
 * completa con el cambio aplicado: así "sacale la crema" no necesita que el sistema sepa editar listas, y lo que vuelve
 * pasa por la vista previa del import como cualquier receta. El catálogo va en el system (cacheable); la receta y el
 * mensaje, en el del usuario.
 */
final class RecipeChatPrompt {

    static final int MAX_OUTPUT_TOKENS = 2048;

    private static final String RULES = """
            Sos el asistente de recetas de un restaurante argentino. Leés un mensaje del equipo (escrito o dictado) que \
            crea o cambia la receta de un plato o de una preparación, y devolvés la receta completa como queda. No \
            guardás nada: una persona la revisa antes de guardarla.

            ENTRADA: "RECETA DE: <nombre> (plato | preparación)"; si ya tiene receta, "RECETA ACTUAL:" con una línea \
            por ingrediente ("ingrediente | cantidad | unidad") y, en una preparación, "RINDE: <cantidad> <unidad>"; \
            después "MENSAJE:" y el mensaje.

            SALIDA, con esta forma:
            {"price":9500,"section":"Principales","yieldQuantity":1.2,"yieldUnit":"kg",
             "ingredients":[{"ingredient":"papas","quantity":300,"unit":"g","catalogName":"Papa"}],
             "proposedProducts":[{"name":"crema","unit":"l"}],
             "warnings":["..."]}

            REGLAS:
            1. ingredients es la receta COMPLETA como queda: la actual con lo que cambia el mensaje. "Sacale la \
            crema" → todos menos la crema; "subí la papa a 300 g" → la papa en 300 g y el resto igual; "agregale 50 g \
            de queso" → la actual más el queso. Lo que el mensaje no cambia va exactamente como en la RECETA ACTUAL \
            (mismo nombre, cantidad y unidad). Si no hay receta actual, o el mensaje da la receta entera, solo lo que \
            dice el mensaje.
            2. quantity y unit: los que dijo para ese ingrediente, nunca los de otro ni los del catálogo ("200 g de \
            acelga y 100 g de harina" → harina 100 y g). En un plato, para UNA porción (si dice "para 4 porciones", dividí cada cantidad y \
            avisalo en warnings); en una preparación, para la tanda que rinde yieldQuantity. unit es una abreviatura \
            de la lista de UNIDADES ("gramos" → g, "kilos" → kg, "litros" → l, "unidades" → u). Números como números, \
            con punto decimal ("medio kilo" → 0.5 y kg; "tres" → 3).
            3. Si un ingrediente es claramente un insumo del CATÁLOGO escrito de otra forma (plural o singular, \
            acentos, mayúsculas: "tomates" y "Tomate"), dejá ingredient como lo dijo el usuario y poné en catalogName \
            el nombre exacto del catálogo. Si no es claramente el mismo ("Tomate" y "Tomate perita"), sin catalogName. \
            Los de la RECETA ACTUAL ya están escritos como en el catálogo.
            4. Un ingrediente nuevo que no está en el CATÁLOGO y no tiene catalogName va además una sola vez a \
            proposedProducts, con el nombre como lo dijo y la unidad típica de compra (g → kg, ml → l, u → u).
            5. Un ingrediente sin cantidad ("sal a gusto", "c/n") queda afuera, con su warning: no pongas 0.
            6. price (precio de venta) y section (paso del menú: entradas, principales…): solo en un plato y solo si el \
            mensaje los dice. yieldQuantity y yieldUnit: solo en una preparación, lo que rinde la tanda; si el mensaje \
            no lo cambia, el RINDE actual.
            7. warnings, en castellano rioplatense y cortos: lo que no pudiste interpretar o tuviste que suponer. No \
            avises por los ingredientes que reconociste del catálogo: ese aviso lo arma el sistema.
            """;

    private RecipeChatPrompt() {}

    /** @param output cómo devolver el resultado: el JSON en el texto (Gemini) o la herramienta (Claude) */
    static String systemPrompt(RecipeMessageRequest r, String output) {
        String products = r.products().isEmpty() ? "(sin insumos cargados)" : r.products().stream()
                .map(p -> p.name() + " | " + (p.unit() == null ? "-" : p.unit()) + (p.preparation() ? " | preparación" : ""))
                .collect(Collectors.joining("\n"));
        return RULES + output + "\n\n"
                + "UNIDADES (abreviatura: nombre): " + r.units().stream()
                        .map(u -> u.abbreviation() + ": " + u.name()).collect(Collectors.joining("; ")) + "\n\n"
                + "CATÁLOGO DE INSUMOS (nombre | unidad | \"preparación\" si tiene receta de tanda):\n" + products;
    }

    static String userMessage(RecipeMessageRequest r) {
        StringBuilder sb = new StringBuilder("RECETA DE: ").append(r.recipeName())
                .append(r.preparation() ? " (preparación)" : " (plato)").append('\n');
        if (r.current() != null) {
            sb.append("RECETA ACTUAL:\n");
            for (RecipeLine l : r.current().ingredients()) {
                sb.append(l.ingredient()).append(" | ").append(plain(l.quantity())).append(" | ")
                        .append(l.unit() == null ? "" : l.unit()).append('\n');
            }
            if (r.preparation() && r.current().yieldQuantity() != null) {
                sb.append("RINDE: ").append(plain(r.current().yieldQuantity())).append(' ')
                        .append(r.current().yieldUnit() == null ? "" : r.current().yieldUnit()).append('\n');
            }
        }
        return sb.append("MENSAJE: ").append(r.message()).toString();
    }

    private static String plain(BigDecimal n) {
        return n == null ? "" : n.stripTrailingZeros().toPlainString();
    }

    /** Para Claude: la forma de la salida como input_schema de la herramienta. */
    static Map<String, Object> jsonSchema() {
        Map<String, Object> ingredient = Map.of("type", "object", "properties", Map.of(
                "ingredient", type("string"), "quantity", type("number"), "unit", type("string"),
                "catalogName", type("string")), "required", List.of("ingredient"));
        Map<String, Object> proposed = Map.of("type", "object", "properties", Map.of(
                "name", type("string"), "unit", type("string")), "required", List.of("name"));
        return Map.of("type", "object", "properties", Map.of(
                "price", type("number"), "section", type("string"),
                "yieldQuantity", type("number"), "yieldUnit", type("string"),
                "ingredients", Map.of("type", "array", "items", ingredient),
                "proposedProducts", Map.of("type", "array", "items", proposed),
                "warnings", Map.of("type", "array", "items", type("string"))),
                "required", List.of("ingredients", "proposedProducts", "warnings"));
    }

    private static Map<String, Object> type(String t) {
        return Map.of("type", t);
    }

    /**
     * Las filas de /catalog-import para una sola receta: {@code row} = la posición del ingrediente. El nombre de la
     * receta no lo elige el modelo: es {@code recipeName}, el del catálogo si ya existe.
     */
    static CatalogSpreadsheetInterpretation parse(JsonNode root, RecipeMessageRequest r) {
        List<RecipeRow> recipes = new ArrayList<>();
        List<PreparationRow> preparations = new ArrayList<>();
        List<IngredientMatch> matches = new ArrayList<>();
        Map<String, Integer> rowOf = new HashMap<>();
        int row = 0;
        for (JsonNode i : root.path("ingredients")) {
            row++;
            boolean first = row == 1;   // el import toma precio, paso y rendimiento de la primera fila que los traiga
            String ingredient = text(i, "ingredient");
            if (r.preparation()) {
                preparations.add(new PreparationRow(row, r.recipeName(), first ? number(root, "yieldQuantity") : null,
                        first ? text(root, "yieldUnit") : null, ingredient, number(i, "quantity"), text(i, "unit")));
            } else {
                recipes.add(new RecipeRow(row, r.recipeName(), first ? number(root, "price") : null,
                        first ? text(root, "section") : null, ingredient, number(i, "quantity"), text(i, "unit")));
            }
            if (ingredient == null) continue;
            rowOf.putIfAbsent(ingredient.toLowerCase(Locale.ROOT), row);
            if (text(i, "catalogName") != null) matches.add(new IngredientMatch(row, ingredient, text(i, "catalogName")));
        }
        List<ProductRow> proposed = new ArrayList<>();
        for (JsonNode p : root.path("proposedProducts")) {
            String name = text(p, "name");
            proposed.add(new ProductRow(name == null ? 1 : rowOf.getOrDefault(name.toLowerCase(Locale.ROOT), 1),
                    name, text(p, "unit"), null, null, null));
        }
        List<String> warnings = new ArrayList<>();
        root.path("warnings").forEach(w -> warnings.add(w.asText()));
        return new CatalogSpreadsheetInterpretation(List.of(), preparations, recipes, proposed, matches, warnings);
    }

    static AppException failed(String why) {
        return new AppException(ErrorCode.AI_INTERPRETATION_FAILED, "Could not interpret the recipe: " + why);
    }
}
