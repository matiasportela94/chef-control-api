package com.chefcontrol.api.catalog.dto;

import com.chefcontrol.application.service.CatalogImportService;

import java.util.List;
import java.util.UUID;

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
        int recipesSkipped,
        int recipesUnchanged,
        List<RecipeReplacement> recipesToReplace,
        int preparationsCreated,
        int preparationsReplaced,
        int preparationsSkipped,
        int preparationsUnchanged,
        List<PreparationReplacement> preparationsToReplace,
        List<ImportRowError> errors
) {
    /**
     * Una preparación que ya tiene receta y la de la planilla la cambiaría: sale en el dryRun y en la confirmación.
     * @param preparation el nombre del insumo en el catálogo
     * @param row         la primera fila de esa preparación en la hoja de preparaciones
     */
    public record PreparationReplacement(UUID productId, String preparation, int row) {}

    /**
     * Un plato que ya tiene receta y la del Excel la cambiaría: sale en el dryRun y en la confirmación, se pise o no.
     * @param menuItem el nombre del plato en el catálogo
     * @param row      la primera fila de ese plato en la hoja de recetas
     */
    public record RecipeReplacement(UUID menuItemId, String menuItem, int row) {}

    /**
     * @param sheet PRODUCTS, RECIPES o PREPARATIONS
     * @param field el campo de la fila (name, unit, category, minStock, yieldPercentage, menuItem, price, section,
     *              ingredient, quantity)
     * @param value lo que vino en ese campo; null si faltaba
     */
    public record ImportRowError(String sheet, int row, String field, String errorCode, String value) {}

    public static CatalogImportResult from(CatalogImportService.Report r) {
        return new CatalogImportResult(r.saved(), r.productsCreated(), r.productsExisting(), r.categoriesCreated(),
                r.sectionsCreated(), r.menuItemsCreated(), r.recipesCreated(), r.recipesReplaced(),
                r.recipesSkipped(), r.recipesUnchanged(),
                r.recipesToReplace().stream().map(x -> new RecipeReplacement(x.menuItemId(), x.menuItem(), x.row())).toList(),
                r.preparationsCreated(), r.preparationsReplaced(), r.preparationsSkipped(), r.preparationsUnchanged(),
                r.preparationsToReplace().stream()
                        .map(x -> new PreparationReplacement(x.productId(), x.preparation(), x.row())).toList(),
                r.errors().stream().map(e -> new ImportRowError(
                        e.sheet().name(), e.row(), e.field(), e.errorCode().name(), e.value())).toList());
    }
}
