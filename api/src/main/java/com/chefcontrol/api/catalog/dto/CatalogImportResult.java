package com.chefcontrol.api.catalog.dto;

import com.chefcontrol.application.service.CatalogImportService;

import java.util.List;

/** Con algún error, o con dryRun, {@code saved} es false y los contadores dicen lo que se haría. */
public record CatalogImportResult(
        boolean saved,
        int productsCreated,
        int productsExisting,
        int categoriesCreated,
        int sectionsCreated,
        int menuItemsCreated,
        int recipesCreated,
        int recipesReplaced,
        List<ImportRowError> errors
) {
    /**
     * @param sheet PRODUCTS o RECIPES
     * @param field el campo de la fila (name, unit, category, minStock, yieldPercentage, menuItem, price, section,
     *              ingredient, quantity)
     * @param value lo que vino en ese campo; null si faltaba
     */
    public record ImportRowError(String sheet, int row, String field, String errorCode, String value) {}

    public static CatalogImportResult from(CatalogImportService.Report r) {
        return new CatalogImportResult(r.saved(), r.productsCreated(), r.productsExisting(), r.categoriesCreated(),
                r.sectionsCreated(), r.menuItemsCreated(), r.recipesCreated(), r.recipesReplaced(),
                r.errors().stream().map(e -> new ImportRowError(
                        e.sheet().name(), e.row(), e.field(), e.errorCode().name(), e.value())).toList());
    }
}
