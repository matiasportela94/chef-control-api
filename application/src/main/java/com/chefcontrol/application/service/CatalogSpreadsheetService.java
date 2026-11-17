package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AIService;
import com.chefcontrol.application.port.AIService.CatalogSpreadsheetInterpretation;
import com.chefcontrol.application.port.AIService.CatalogSpreadsheetRequest;
import com.chefcontrol.application.port.AIService.IngredientMatch;
import com.chefcontrol.application.port.AIService.SpreadsheetRow;
import com.chefcontrol.application.port.AIService.SpreadsheetSheet;
import com.chefcontrol.application.port.AIService.UnitOption;
import com.chefcontrol.application.service.CatalogImportService.PreparationRow;
import com.chefcontrol.application.service.CatalogImportService.ProductRow;
import com.chefcontrol.application.service.CatalogImportService.RecipeRow;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.menu.MenuSection;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.repository.MenuItemRepository;
import com.chefcontrol.domain.repository.MenuSectionRepository;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.UnitRepository;
import com.chefcontrol.domain.shared.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * La IA traduce un Excel de catálogo armado como sea al cuerpo de /catalog-import. Solo traduce: no escribe nada,
 * y lo que devuelve pasa por la vista previa (dryRun) y la confirmación de siempre, así que el modelo nunca da de
 * alta un insumo por su cuenta.
 */
@Service
@RequiredArgsConstructor
public class CatalogSpreadsheetService {

    /**
     * Filas con datos por pedido. Medido con gemini-2.5-flash (2026-10-23): ~18 tokens de salida por fila, 308 filas
     * = 5.525 tokens en 22 s. Con el máximo de 8.192 que piden los adaptadores, 300 filas dejan casi la mitad de
     * margen para nombres largos y avisos.
     */
    public static final int MAX_ROWS = 300;

    private static final PageRequest ALL = PageRequest.of(0, Integer.MAX_VALUE);

    private final AIService aiService;
    private final UnitRepository unitRepository;
    private final ProductRepository productRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuSectionRepository menuSectionRepository;

    public CatalogSpreadsheetInterpretation interpretCatalogSpreadsheet(List<SpreadsheetSheet> sheets) {
        UUID restaurantId = TenantContext.require();
        // Las filas vacías no le sirven al modelo y gastan tokens.
        List<SpreadsheetSheet> withData = sheets.stream()
                .map(s -> new SpreadsheetSheet(s.name(), s.rows().stream().filter(CatalogSpreadsheetService::hasData).toList()))
                .filter(s -> !s.rows().isEmpty())
                .toList();
        int rows = withData.stream().mapToInt(s -> s.rows().size()).sum();
        if (rows == 0) {
            return new CatalogSpreadsheetInterpretation(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
        if (rows > MAX_ROWS) {
            throw AppException.badRequest(ErrorCode.AI_SPREADSHEET_TOO_LARGE,
                    "The spreadsheet has " + rows + " rows with data; the limit is " + MAX_ROWS);
        }

        List<String> productNames = productRepository.findByRestaurantIdAndIsActiveTrue(restaurantId, ALL).content()
                .stream().map(Product::getName).toList();
        CatalogSpreadsheetInterpretation result = aiService.interpretCatalogSpreadsheet(new CatalogSpreadsheetRequest(
                withData,
                unitRepository.findAll().stream().map(u -> new UnitOption(u.getName(), u.getAbbreviation())).toList(),
                productNames,
                menuItemRepository.findByRestaurantIdAndActive(restaurantId, true, ALL).content().stream()
                        .map(MenuItem::getName).toList(),
                menuSectionRepository.findAllByRestaurantId(restaurantId).stream().map(MenuSection::getName).toList()));
        return applyMatches(result, productNames);
    }

    /**
     * Lo que el modelo reconoce como un insumo existente escrito de otra forma ("Tomates" → "Tomate") se aplica acá y
     * no en el modelo: en la prueba del 2026-10-23 gemini-2.5-flash avisó "usé 'Tomate'" y dejó "Tomates" en la fila.
     * Así el aviso siempre dice lo que pasó con los datos, y un nombre que no existe no se acepta: se reemplaza por
     * la escritura del catálogo (o de las filas de insumos) que coincide con la comparación del import.
     *
     * <p>Un "insumo" que el modelo sacó de una fila de receta (misma fila que un ingrediente) es una propuesta aunque
     * lo haya puesto en products: pasó en la prueba con "limon". Y un insumo propuesto que ya existe, ya viene en las
     * filas de insumos, ya se propuso o es un nombre que se acaba de reconocer se descarta: proponerlo crearía justo
     * el duplicado que la propuesta quiere evitar.
     */
    private static CatalogSpreadsheetInterpretation applyMatches(CatalogSpreadsheetInterpretation r, List<String> catalog) {
        Set<Integer> recipeRows = new HashSet<>();
        r.recipes().forEach(x -> recipeRows.add(x.row()));
        r.preparations().forEach(x -> recipeRows.add(x.row()));
        List<ProductRow> products = r.products().stream().filter(p -> !recipeRows.contains(p.row())).toList();
        List<ProductRow> candidates = new ArrayList<>(r.products().stream().filter(p -> recipeRows.contains(p.row())).toList());
        candidates.addAll(r.proposedProducts());

        Map<String, String> known = new HashMap<>();   // clave del import → cómo está escrito
        Set<String> inCatalog = new HashSet<>();
        for (String name : catalog) {
            known.putIfAbsent(CatalogImportService.key(name), name);
            inCatalog.add(CatalogImportService.key(name));
        }
        products.stream().filter(p -> p.name() != null)
                .forEach(p -> known.putIfAbsent(CatalogImportService.key(p.name()), p.name()));

        Map<Integer, String> renamed = new HashMap<>();
        Set<String> recognized = new HashSet<>();
        List<String> warnings = new ArrayList<>(r.warnings());
        for (IngredientMatch m : r.ingredientMatches()) {
            String target = known.get(CatalogImportService.key(m.catalogName()));
            if (target == null) continue;   // el modelo nombró algo que no existe: queda como vino
            recognized.add(CatalogImportService.key(m.writtenName()));
            if (CatalogImportService.key(target).equals(CatalogImportService.key(m.writtenName()))) continue; // solo mayúsculas o acentos: el import ya los iguala
            renamed.put(m.row(), target);
            warnings.add("Usé '" + target + "' " + (inCatalog.contains(CatalogImportService.key(target))
                    ? "del catálogo" : "de la hoja de insumos") + " para '" + m.writtenName() + "'."); // sin fila: el row es el ref del front, no la fila del Excel
        }
        List<RecipeRow> recipes = r.recipes().stream()
                .map(x -> renamed.containsKey(x.row()) ? new RecipeRow(x.row(), x.menuItem(), x.price(), x.section(),
                        renamed.get(x.row()), x.quantity(), x.unit()) : x)
                .toList();
        List<PreparationRow> preparations = r.preparations().stream()
                .map(x -> renamed.containsKey(x.row()) ? new PreparationRow(x.row(), x.preparation(), x.yieldQuantity(),
                        x.yieldUnit(), renamed.get(x.row()), x.quantity(), x.unit()) : x)
                .toList();

        Set<String> taken = new HashSet<>(known.keySet());
        taken.addAll(recognized);
        // Una preparación de la planilla se crea desde su hoja: proponerla además como insumo la duplicaría.
        preparations.stream().filter(x -> x.preparation() != null)
                .forEach(x -> taken.add(CatalogImportService.key(x.preparation())));
        List<ProductRow> proposed = candidates.stream()
                .filter(p -> p.name() != null && taken.add(CatalogImportService.key(p.name())))
                .toList();
        return new CatalogSpreadsheetInterpretation(products, preparations, recipes, proposed, List.of(), warnings);
    }

    private static boolean hasData(SpreadsheetRow row) {
        return row.cells() != null
                && row.cells().stream().anyMatch(c -> c != null && !c.toString().isBlank());
    }
}
