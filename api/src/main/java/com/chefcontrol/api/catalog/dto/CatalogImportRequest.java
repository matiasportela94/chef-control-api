package com.chefcontrol.api.catalog.dto;

import com.chefcontrol.application.service.CatalogImportService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Las filas tal como vienen del Excel. Sin validaciones por campo a propósito: un problema de una fila vuelve
 * como error de esa fila en el resultado, no como un 400 de toda la request.
 */
public record CatalogImportRequest(
        @Size(max = CatalogImportService.MAX_PRODUCT_ROWS) List<@NotNull ProductImportRow> products,
        @Size(max = CatalogImportService.MAX_RECIPE_ROWS) List<@NotNull RecipeImportRow> recipes
) {
    public CatalogImportRequest {
        products = products == null ? List.of() : products;
        recipes = recipes == null ? List.of() : recipes;
    }

    /** @param row el número de fila del Excel */
    public record ProductImportRow(int row, String name, String unit, String category,
                                   BigDecimal minStock, BigDecimal yieldPercentage) {}

    /** Una fila por ingrediente; las del mismo plato forman su receta. {@code quantity} es para un plato. */
    public record RecipeImportRow(int row, String menuItem, BigDecimal price, String section,
                                  String ingredient, BigDecimal quantity, String unit) {}
}
