package com.chefcontrol.infrastructure.ai;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AIService.CatalogSpreadsheetInterpretation;
import com.chefcontrol.application.port.AIService.CatalogSpreadsheetRequest;
import com.chefcontrol.application.port.AIService.IngredientMatch;
import com.chefcontrol.application.port.AIService.SpreadsheetRow;
import com.chefcontrol.application.port.AIService.SpreadsheetSheet;
import com.chefcontrol.application.service.CatalogImportService.PreparationRow;
import com.chefcontrol.application.service.CatalogImportService.ProductRow;
import com.chefcontrol.application.service.CatalogImportService.RecipeRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * El prompt y la lectura de la respuesta de "interpretar una planilla de catálogo", iguales para Claude y Gemini:
 * cada adaptador solo pone el transporte. La salida pide los platos agrupados (el nombre no se repite por
 * ingrediente) y se aplana acá a una fila por ingrediente, que es lo que espera /catalog-import.
 */
final class CatalogSpreadsheetPrompt {

    /** Con este máximo se midió el tope de filas ({@code CatalogSpreadsheetService.MAX_ROWS}). */
    static final int MAX_OUTPUT_TOKENS = 8192;

    private static final String RULES = """
            Traducís planillas de restaurantes argentinos al formato de importación de Chef Control. Recibís las \
            filas de una o más hojas de Excel, con el formato que haya armado el cliente, y devolvés los insumos y \
            las recetas que encuentres. No guardás nada: una persona revisa lo que devuelvas antes de importarlo.

            ENTRADA: cada hoja empieza con "## Hoja: <nombre>" y cada fila es "ref | celda | celda | ...". ref \
            identifica la fila; una celda vacía queda en blanco.

            SALIDA, con esta forma:
            {"products":[{"row":ref,"name":"...","unit":"kg","category":"...","minStock":10,"yieldPercentage":90}],
             "dishes":[{"menuItem":"...","price":9500,"section":"...","ingredients":[{"row":ref,"ingredient":"...","quantity":0.25,"unit":"kg","catalogName":"..."}]}],
             "preparations":[{"preparation":"...","yieldQuantity":1.2,"yieldUnit":"kg","ingredients":[{"row":ref,"ingredient":"...","quantity":0.5,"unit":"l","catalogName":"..."}]}],
             "proposedProducts":[{"row":ref,"name":"...","unit":"kg"}],
             "warnings":["..."]}
            - products: los insumos (lo que el restaurante compra). category, minStock y yieldPercentage solo si la \
            planilla los trae.
            - dishes: los platos con su receta. price (precio de venta) y section (paso del menú: entradas, \
            principales…) solo si la planilla los trae.
            - preparations: las recetas intermedias que el restaurante hace y usa como ingrediente (ver regla 9), \
            con cuánto rinde la tanda.
            - proposedProducts: insumos que proponés vos porque una receta los usa y no están ni en el CATÁLOGO ni \
            en las filas de insumos de la planilla (ver regla 6).
            - row: el ref de la fila de donde sale el dato. En una receta, el ref de la fila del ingrediente. En \
            proposedProducts, el ref de la primera fila de receta donde aparece el ingrediente.
            - Números como números: sin "$", sin separador de miles, con punto decimal ("$ 9.500" → 9500, "0,05" → 0.05).

            REGLAS:
            1. Copiá los nombres tal cual aparecen; solo sacá espacios de más. No corrijas, no traduzcas, no \
            completes y no inventes nada que no esté en la planilla.
            2. unit es siempre una abreviatura de la lista de UNIDADES. Mapeá lo que escribió el cliente: "kilos", \
            "Kg.", "kgs" → kg; "gr", "grs", "gramos" → g; "lts", "litro" → l; "unid", "u.", "c/u" → u. Si la \
            cantidad viene como texto, con fracción o en otra unidad ("½ kg", "medio kilo", "1/4 l"), pasala a un \
            número en una unidad de la lista ("½ kg" → 500 g). Si no hay unidad, no la adivines: dejala afuera.
            3. Las cantidades de una receta son para UN plato. Si la receta dice que rinde N porciones, dividí cada \
            cantidad por N y avisalo en warnings.
            4. Ignorá títulos, encabezados, totales, subtotales, costos, notas y filas vacías. No avises por títulos \
            ni encabezados; sí por los totales, costos y notas que ignores, en un solo warning.
            5. Usá las listas del CATÁLOGO para reconocer qué es cada cosa (un insumo, un plato, un paso). Si el \
            ingrediente de una receta es claramente un insumo del CATÁLOGO o de las filas de insumos de la planilla \
            escrito de otra forma (plural o singular, acentos, mayúsculas: "Tomates" y "Tomate"), dejá ingredient \
            como está en la planilla y poné en catalogName el nombre exacto del catálogo o de la planilla. Si no es \
            claramente el mismo ("Tomate" y "Tomate perita"), no pongas catalogName. No avises nada de esto: el \
            sistema arma el aviso.
            6. products sale solo de filas que describen insumos. Si una receta usa un ingrediente que no está en el \
            CATÁLOGO ni en las filas de insumos y no le pusiste catalogName, agregalo una sola vez a proposedProducts: \
            con el nombre como aparece en la receta, la unidad típica de compra para el tipo de unidad de la receta \
            (g → kg, ml → l, u → u; si no es obvia, la misma de la receta) y sin categoría, salvo que la planilla la \
            diga. Ante la duda entre "es uno del catálogo" y "es otro", proponelo: una persona lo revisa.
            7. Si un dato no se puede interpretar con seguridad, no lo adivines: dejá afuera esa fila y explicá en \
            warnings cuál y por qué. Un ingrediente sin cantidad numérica ("c/n", "a gusto", "cantidad \
            necesaria") queda afuera de la receta y de proposedProducts, con su warning: no pongas 0.
            8. Los warnings van en castellano rioplatense, cortos, para mostrarle al usuario. Nombran el insumo, el \
            plato o la hoja, nunca números de fila ni refs: el ref no es la fila del Excel que ve el usuario.
            9. Una preparación es una receta que el restaurante hace en tandas y usa como ingrediente de un plato \
            o de otra preparación (mayonesa base, salsas, masas, fondos, medallones). Si una receta dice cuánto rinde \
            ("rinde 1,2 kg") o la usa otro plato como ingrediente, va en preparations y no en dishes, con yieldQuantity \
            y yieldUnit (una unidad de la lista). Si no dice cuánto rinde, ponela igual sin yieldQuantity y avisalo. \
            Sus cantidades son para esa tanda: no las dividas por porciones. Quien la usa pone su nombre tal cual como \
            ingredient. Una preparación nunca va a products ni a proposedProducts.
            """;

    private CatalogSpreadsheetPrompt() {}

    /** @param output cómo devolver el resultado: el JSON en el texto (Gemini) o la herramienta (Claude) */
    static String systemPrompt(CatalogSpreadsheetRequest r, String output) {
        return RULES + output + "\n\nCATÁLOGO\n"
                + "UNIDADES (nombre: abreviatura): " + r.units().stream()
                        .map(u -> u.name() + ": " + u.abbreviation()).collect(Collectors.joining("; ")) + "\n"
                + "INSUMOS: " + list(r.productNames()) + "\n"
                + "PLATOS: " + list(r.menuItemNames()) + "\n"
                + "PASOS DEL MENÚ: " + list(r.sectionNames());
    }

    static String rows(CatalogSpreadsheetRequest r) {
        StringBuilder sb = new StringBuilder();
        for (SpreadsheetSheet sheet : r.sheets()) {
            sb.append("## Hoja: ").append(sheet.name() == null ? "" : sheet.name()).append('\n');
            for (SpreadsheetRow row : sheet.rows()) {
                sb.append(row.ref());
                for (Object c : row.cells()) sb.append(" | ").append(cell(c));
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    static int rowCount(CatalogSpreadsheetRequest r) {
        return r.sheets().stream().mapToInt(s -> s.rows().size()).sum();
    }

    /** Los números de Excel llegan como double, y toString escribiría 10000000 como "1.0E7". */
    static String cell(Object c) {
        if (c == null) return "";
        if (c instanceof Double || c instanceof Float) {
            return BigDecimal.valueOf(((Number) c).doubleValue()).stripTrailingZeros().toPlainString();
        }
        if (c instanceof BigDecimal d) return d.stripTrailingZeros().toPlainString();
        // Un "|" o un salto de línea dentro de una celda romperían el formato de la fila.
        return c.toString().replace('\n', ' ').replace('\r', ' ').replace('|', '/').trim();
    }

    private static String list(List<String> names) {
        return names.isEmpty() ? "(ninguno)" : String.join("; ", names);
    }

    /** Para Claude: la forma de la salida como input_schema de la herramienta. */
    static Map<String, Object> jsonSchema() {
        Map<String, Object> product = object(Map.of(
                "row", type("integer"), "name", type("string"), "unit", type("string"),
                "category", type("string"), "minStock", type("number"), "yieldPercentage", type("number")),
                List.of("row", "name"));
        Map<String, Object> ingredient = object(Map.of(
                "row", type("integer"), "ingredient", type("string"), "quantity", type("number"), "unit", type("string"),
                "catalogName", type("string")),
                List.of("row", "ingredient"));
        Map<String, Object> dish = object(Map.of(
                "menuItem", type("string"), "price", type("number"), "section", type("string"),
                "ingredients", Map.of("type", "array", "items", ingredient)),
                List.of("menuItem", "ingredients"));
        Map<String, Object> preparation = object(Map.of(
                "preparation", type("string"), "yieldQuantity", type("number"), "yieldUnit", type("string"),
                "ingredients", Map.of("type", "array", "items", ingredient)),
                List.of("preparation", "ingredients"));
        return object(Map.of(
                "products", Map.of("type", "array", "items", product),
                "dishes", Map.of("type", "array", "items", dish),
                "preparations", Map.of("type", "array", "items", preparation),
                "proposedProducts", Map.of("type", "array", "items", product),
                "warnings", Map.of("type", "array", "items", type("string"))),
                List.of("products", "dishes", "preparations", "proposedProducts", "warnings"));
    }

    private static Map<String, Object> type(String t) {
        return Map.of("type", t);
    }

    private static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required);
    }

    /** Para Gemini: el JSON viene en el texto, a veces envuelto en ```json. */
    static JsonNode extractJson(String text, ObjectMapper mapper) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) throw failed("no JSON in the model's answer");
        try {
            return mapper.readTree(text.substring(start, end + 1));
        } catch (Exception e) {
            throw failed("unreadable JSON: " + e.getMessage());
        }
    }

    static CatalogSpreadsheetInterpretation parse(JsonNode root) {
        List<ProductRow> products = productRows(root.path("products"));
        List<RecipeRow> recipes = new ArrayList<>();
        List<IngredientMatch> matches = new ArrayList<>();
        for (JsonNode d : root.path("dishes")) {
            boolean first = true;
            for (JsonNode i : d.path("ingredients")) {
                // Precio y paso en la primera fila del plato: el import toma los de la primera fila que los traiga.
                recipes.add(new RecipeRow(i.path("row").asInt(0), text(d, "menuItem"),
                        first ? number(d, "price") : null, first ? text(d, "section") : null,
                        text(i, "ingredient"), number(i, "quantity"), text(i, "unit")));
                if (text(i, "catalogName") != null && text(i, "ingredient") != null) {
                    matches.add(new IngredientMatch(i.path("row").asInt(0), text(i, "ingredient"), text(i, "catalogName")));
                }
                first = false;
            }
        }
        List<PreparationRow> preparations = new ArrayList<>();
        for (JsonNode d : root.path("preparations")) {
            boolean first = true;
            for (JsonNode i : d.path("ingredients")) {
                // El rendimiento en la primera fila: el import toma el de la primera fila que lo traiga.
                preparations.add(new PreparationRow(i.path("row").asInt(0), text(d, "preparation"),
                        first ? number(d, "yieldQuantity") : null, first ? text(d, "yieldUnit") : null,
                        text(i, "ingredient"), number(i, "quantity"), text(i, "unit")));
                if (text(i, "catalogName") != null && text(i, "ingredient") != null) {
                    matches.add(new IngredientMatch(i.path("row").asInt(0), text(i, "ingredient"), text(i, "catalogName")));
                }
                first = false;
            }
        }
        List<String> warnings = new ArrayList<>();
        root.path("warnings").forEach(w -> warnings.add(w.asText()));
        return new CatalogSpreadsheetInterpretation(products, preparations, recipes,
                productRows(root.path("proposedProducts")), matches, warnings);
    }

    private static List<ProductRow> productRows(JsonNode array) {
        List<ProductRow> rows = new ArrayList<>();
        for (JsonNode p : array) {
            rows.add(new ProductRow(p.path("row").asInt(0), text(p, "name"), text(p, "unit"),
                    text(p, "category"), number(p, "minStock"), number(p, "yieldPercentage")));
        }
        return rows;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText().trim();
    }

    /** Un número que vino como texto se intenta leer; si no se puede, queda vacío y el import lo marca en su fila. */
    private static BigDecimal number(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        if (v.isNumber()) return v.decimalValue();
        try {
            return new BigDecimal(v.asText().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static AppException failed(String why) {
        return new AppException(ErrorCode.AI_INTERPRETATION_FAILED, "Could not interpret the spreadsheet: " + why);
    }

    static AppException tooLarge() {
        return new AppException(ErrorCode.AI_SPREADSHEET_TOO_LARGE, "The answer did not fit in the model's output limit");
    }
}
