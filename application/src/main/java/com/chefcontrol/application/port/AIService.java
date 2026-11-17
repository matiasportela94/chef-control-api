package com.chefcontrol.application.port;

import com.chefcontrol.application.service.CatalogImportService.PreparationRow;
import com.chefcontrol.application.service.CatalogImportService.ProductRow;
import com.chefcontrol.application.service.CatalogImportService.RecipeRow;

import java.util.List;
import java.util.UUID;

public interface AIService {

    AIInterpretation interpret(InterpretRequest request);

    /**
     * Traduce una planilla de catálogo armada como sea a las filas de /catalog-import. No escribe nada: lo que
     * devuelve pasa después por la vista previa (dryRun) y la confirmación de siempre. Lanza
     * {@code AI_SPREADSHEET_TOO_LARGE} si la salida no entra en el máximo del modelo y
     * {@code AI_INTERPRETATION_FAILED} si el modelo no responde o no se entiende lo que respondió.
     */
    CatalogSpreadsheetInterpretation interpretCatalogSpreadsheet(CatalogSpreadsheetRequest request);

    record InterpretRequest(
        String message,
        UUID restaurantId,
        UUID sessionId,
        String catalog
    ) {}

    record AIInterpretation(
        String intent,
        int confidence,
        boolean needsConfirmation,
        Object extractedData,
        String responseToUser
    ) {}

    /** Lo que ya existe va para que el modelo reconozca qué es cada cosa, no para renombrar. */
    record CatalogSpreadsheetRequest(
        List<SpreadsheetSheet> sheets,
        List<UnitOption> units,
        List<String> productNames,
        List<String> menuItemNames,
        List<String> sectionNames
    ) {}

    record SpreadsheetSheet(String name, List<SpreadsheetRow> rows) {}

    /** @param ref número global de la fila que pone el front, para volver a la hoja y fila originales */
    record SpreadsheetRow(int ref, List<Object> cells) {}

    record UnitOption(String name, String abbreviation) {}

    /**
     * {@code row} de cada fila = el {@code ref} de la fila de origen.
     *
     * @param proposedProducts insumos que arma la IA porque una receta los usa y no existen ni en el catálogo ni en
     *                         las filas de insumos; {@code row} = la primera fila de receta que los usa. El front los
     *                         muestra como propuestos y recién los suma a {@code products} para la vista previa.
     * @param ingredientMatches  ingredientes que el modelo reconoce como un insumo existente escrito de otra forma
     *                           ("Tomates" → "Tomate"). Los aplica el servicio, que verifica que el nombre exista;
     *                           lo que devuelve el servicio ya los trae aplicados y esta lista vacía.
     */
    record CatalogSpreadsheetInterpretation(
        List<ProductRow> products,
        List<PreparationRow> preparations,
        List<RecipeRow> recipes,
        List<ProductRow> proposedProducts,
        List<IngredientMatch> ingredientMatches,
        List<String> warnings
    ) {}

    /**
     * @param row la fila de receta (o de preparación); {@code writtenName} como vino, {@code catalogName} el insumo que el
     *            modelo reconoce
     */
    record IngredientMatch(int row, String writtenName, String catalogName) {}
}
