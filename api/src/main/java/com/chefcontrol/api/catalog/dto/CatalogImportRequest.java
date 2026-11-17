package com.chefcontrol.api.catalog.dto;

import com.chefcontrol.application.service.CatalogImportService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Las filas tal como vienen del Excel. Sin validaciones por campo a propósito: un problema de una fila vuelve
 * como error de esa fila en el resultado, no como un 400 de toda la request.
 */
public record CatalogImportRequest(
        @Size(max = CatalogImportService.MAX_PRODUCT_ROWS) List<@NotNull ProductImportRow> products,
        @Size(max = CatalogImportService.MAX_RECIPE_ROWS) List<@NotNull RecipeImportRow> recipes,
        /** Los platos existentes cuya receta se puede pisar (los {@code recipesToReplace} del dryRun). Sin esto, no se pisa ninguna. */
        @Size(max = CatalogImportService.MAX_RECIPE_ROWS) List<@NotNull UUID> replaceRecipeMenuItemIds,
        /** La hoja "Preparaciones": una fila por ingrediente. */
        @Size(max = CatalogImportService.MAX_PREPARATION_ROWS) List<@NotNull PreparationImportRow> preparations,
        /** Los insumos cuya receta de preparación se puede pisar (los {@code preparationsToReplace} del dryRun). */
        @Size(max = CatalogImportService.MAX_PREPARATION_ROWS) List<@NotNull UUID> replacePreparationProductIds
) {
    public CatalogImportRequest {
        products = products == null ? List.of() : products;
        recipes = recipes == null ? List.of() : recipes;
        replaceRecipeMenuItemIds = replaceRecipeMenuItemIds == null ? List.of() : replaceRecipeMenuItemIds;
        preparations = preparations == null ? List.of() : preparations;
        replacePreparationProductIds = replacePreparationProductIds == null ? List.of() : replacePreparationProductIds;
    }

    /**
     * {@code quantity} es para la tanda que rinde {@code yieldQuantity} {@code yieldUnit}, que van en la primera fila de la
     * preparación (o en cualquiera: se toma la primera que los traiga).
     */
    public record PreparationImportRow(int row, String preparation, BigDecimal yieldQuantity, String yieldUnit,
                                       String ingredient, BigDecimal quantity, String unit) {}

    /** @param row el número de fila del Excel */
    public record ProductImportRow(int row, String name, String unit, String category,
                                   BigDecimal minStock, BigDecimal yieldPercentage, BigDecimal ivaPercentage) {}

    /** Una fila por ingrediente; las del mismo plato forman su receta. {@code quantity} es para un plato. */
    public record RecipeImportRow(int row, String menuItem, BigDecimal price, String section,
                                  String ingredient, BigDecimal quantity, String unit) {}
}
