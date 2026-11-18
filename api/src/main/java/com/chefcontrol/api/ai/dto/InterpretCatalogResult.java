package com.chefcontrol.api.ai.dto;

import com.chefcontrol.api.catalog.dto.CatalogImportRequest.PreparationImportRow;
import com.chefcontrol.api.catalog.dto.CatalogImportRequest.ProductImportRow;
import com.chefcontrol.api.catalog.dto.CatalogImportRequest.RecipeImportRow;
import com.chefcontrol.application.port.AIService.CatalogSpreadsheetInterpretation;
import com.chefcontrol.application.service.CatalogImportService.ProductRow;

import java.util.List;

/**
 * Lo que la IA arma para /catalog-import: las filas son las del import, tal cual. Lo devuelven /ai/interpret-catalog
 * ({@code row} = el ref de la fila del Excel) y el chat cuando el mensaje es una receta ({@code row} = la posición del
 * ingrediente: 1, 2, 3…).
 * {@code proposedProducts}: insumos que arma la IA porque una receta los usa y no existen; {@code row} = la primera
 * fila de receta que los usa. El front los muestra como propuestos y los suma a products para la vista previa.
 */
public record InterpretCatalogResult(List<ProductImportRow> products, List<PreparationImportRow> preparations,
                                     List<RecipeImportRow> recipes,
                                     List<ProductImportRow> proposedProducts, List<String> warnings) {

    public static InterpretCatalogResult from(CatalogSpreadsheetInterpretation r) {
        return new InterpretCatalogResult(
                r.products().stream().map(InterpretCatalogResult::toImportRow).toList(),
                r.preparations().stream().map(x -> new PreparationImportRow(x.row(), x.preparation(), x.yieldQuantity(),
                        x.yieldUnit(), x.ingredient(), x.quantity(), x.unit())).toList(),
                r.recipes().stream().map(x -> new RecipeImportRow(
                        x.row(), x.menuItem(), x.price(), x.section(), x.ingredient(), x.quantity(), x.unit())).toList(),
                r.proposedProducts().stream().map(InterpretCatalogResult::toImportRow).toList(),
                r.warnings());
    }

    private static ProductImportRow toImportRow(ProductRow p) {
        return new ProductImportRow(p.row(), p.name(), p.unit(), p.category(), p.minStock(), p.yieldPercentage(),
                p.ivaPercentage());
    }
}
