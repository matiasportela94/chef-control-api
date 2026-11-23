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

import java.math.BigDecimal;
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
        return applyMatches(result, productNames, unitRepository.findAll());
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
     *
     * <p>Lo mismo con un plural o singular que el modelo no marcó ("huevos" y "Huevo"): pasó en la prueba del 2026-11-18
     * con una receta por chat, y el "huevos" propuesto se habría creado duplicado.
     */
    static CatalogSpreadsheetInterpretation applyMatches(CatalogSpreadsheetInterpretation r, List<String> catalog,
                                                         List<com.chefcontrol.domain.product.Unit> units) {
        r = perServing(r);
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
            renamed.put(m.row(), target);
            // Solo mayúsculas o acentos: se escribe como en el catálogo, sin aviso (el import ya los igualaba).
            if (CatalogImportService.key(target).equals(CatalogImportService.key(m.writtenName()))) continue;
            warnings.add("Usé '" + target + "' " + (inCatalog.contains(CatalogImportService.key(target))
                    ? "del catálogo" : "de la hoja de insumos") + " para '" + m.writtenName() + "'."); // sin fila: el row es el ref del front, no la fila del Excel
        }
        Map<Integer, String> ingredients = new HashMap<>();
        r.recipes().forEach(x -> ingredients.put(x.row(), x.ingredient()));
        r.preparations().forEach(x -> ingredients.put(x.row(), x.ingredient()));
        Set<String> warned = new HashSet<>();
        ingredients.forEach((row, written) -> {
            if (written == null || renamed.containsKey(row)) return;
            String exact = known.get(CatalogImportService.key(written));
            if (exact != null) {   // "harina" y el catálogo dice "Harina": la escritura del catálogo, sin aviso
                renamed.put(row, exact);
                return;
            }
            String target = singularMatch(written, known.values());
            if (target == null) return;
            renamed.put(row, target);
            recognized.add(CatalogImportService.key(written));
            if (warned.add(CatalogImportService.key(written))) {
                warnings.add("Usé '" + target + "' " + (inCatalog.contains(CatalogImportService.key(target))
                        ? "del catálogo" : "de la hoja de insumos") + " para '" + written + "'.");
            }
        });
        Set<String> taken = new HashSet<>(known.keySet());
        taken.addAll(recognized);
        // Una preparación de la planilla se crea desde su hoja: proponerla además como insumo la duplicaría.
        r.preparations().stream().filter(x -> x.preparation() != null)
                .forEach(x -> taken.add(CatalogImportService.key(x.preparation())));
        // Un insumo nuevo sale con mayúscula ("acelga" → "Acelga"), y las filas que lo usan, escritas igual.
        List<ProductRow> proposed = candidates.stream()
                .filter(p -> p.name() != null && singularMatch(p.name(), known.values()) == null
                        && taken.add(CatalogImportService.key(p.name())))
                .map(p -> new ProductRow(p.row(), capitalized(p.name()), p.unit(), p.category(), p.minStock(),
                        p.yieldPercentage(), p.ivaPercentage()))
                .toList();
        Map<String, String> proposedNames = new HashMap<>();
        proposed.forEach(p -> proposedNames.put(CatalogImportService.key(p.name()), p.name()));
        java.util.function.BiFunction<Integer, String, String> spelled = (row, written) -> renamed.containsKey(row)
                ? renamed.get(row)
                : written == null ? null : proposedNames.getOrDefault(CatalogImportService.key(written), written);

        List<RecipeRow> recipes = r.recipes().stream()
                .map(x -> new RecipeRow(x.row(), x.menuItem(), x.price(), x.section(),
                        spelled.apply(x.row(), x.ingredient()), x.quantity(), x.unit()))
                .toList();
        List<PreparationRow> preparations = r.preparations().stream()
                .map(x -> new PreparationRow(x.row(), x.preparation(), x.yieldQuantity(), x.yieldUnit(),
                        spelled.apply(x.row(), x.ingredient()), x.quantity(), x.unit()))
                .toList();
        // Un ingrediente que quedó afuera ("nuez moscada c/n", una taza) no se propone: se crearía un insumo que no usa
        // nadie (pasó con la foto de una receta, 2026-11-19).
        Set<String> used = new HashSet<>();
        recipes.forEach(x -> { if (x.ingredient() != null) used.add(CatalogImportService.key(x.ingredient())); });
        preparations.forEach(x -> { if (x.ingredient() != null) used.add(CatalogImportService.key(x.ingredient())); });
        List<ProductRow> usedProposed = proposed.stream().filter(p -> used.contains(CatalogImportService.key(p.name()))).toList();
        return new CatalogSpreadsheetInterpretation(products, preparations, recipes,
                inRecipeUnit(usedProposed, recipes, preparations, units), List.of(), warnings);
    }

    /**
     * Un insumo propuesto cuya unidad no convierte con la de una receta que lo usa ("1 cebolla" y la cebolla propuesta en
     * kg) va en la de la receta: si no, el import rechaza la fila con IMPORT_INCOMPATIBLE_UNIT. Pasó en la prueba del
     * 2026-11-19 con una foto de receta; la unidad de compra la elige el modelo y a veces no cierra.
     * ponytail: "convierte" = misma unidad base, el modelo de UnitConversionService; si alguna vez convierte por insumo
     * (el peso de una unidad), esto tiene que preguntarle a él.
     */
    static List<ProductRow> inRecipeUnit(List<ProductRow> proposed, List<RecipeRow> recipes, List<PreparationRow> preparations,
                                         List<com.chefcontrol.domain.product.Unit> units) {
        Map<String, com.chefcontrol.domain.product.Unit> byName = new HashMap<>();
        for (var u : units) {
            if (u.getAbbreviation() != null) byName.putIfAbsent(CatalogImportService.key(u.getAbbreviation()), u);
            if (u.getName() != null) byName.putIfAbsent(CatalogImportService.key(u.getName()), u);
        }
        java.util.function.Function<String, UUID> base = name -> {
            var u = name == null ? null : byName.get(CatalogImportService.key(name));
            return u == null ? null : u.getBaseUnitId() != null ? u.getBaseUnitId() : u.getId();
        };
        Map<String, String> usedIn = new HashMap<>();   // insumo → la unidad de la primera fila que lo usa
        recipes.forEach(x -> { if (x.ingredient() != null && x.unit() != null) usedIn.putIfAbsent(CatalogImportService.key(x.ingredient()), x.unit()); });
        preparations.forEach(x -> { if (x.ingredient() != null && x.unit() != null) usedIn.putIfAbsent(CatalogImportService.key(x.ingredient()), x.unit()); });
        return proposed.stream().map(p -> {
            String used = p.name() == null ? null : usedIn.get(CatalogImportService.key(p.name()));
            UUID mine = base.apply(p.unit());
            UUID theirs = base.apply(used);
            return theirs == null || theirs.equals(mine) ? p
                    : new ProductRow(p.row(), p.name(), used, p.category(), p.minStock(), p.yieldPercentage(), p.ivaPercentage());
        }).toList();
    }

    /**
     * "Rinde 4 porciones" con las cantidades de las 4: el import guarda la receta de un plato, así que se dividen acá y
     * se avisa. Lo hace el back y no el modelo, que no hace cuentas (antes el prompt le pedía dividir).
     */
    static CatalogSpreadsheetInterpretation perServing(CatalogSpreadsheetInterpretation r) {
        Map<String, BigDecimal> servings = new HashMap<>();
        Map<String, String> named = new HashMap<>();
        for (AIService.DishServings s : r.dishServings()) {
            if (s.menuItem() == null || s.servings() == null || s.servings().compareTo(BigDecimal.ONE) <= 0) continue;
            servings.putIfAbsent(CatalogImportService.key(s.menuItem()), s.servings());
            named.putIfAbsent(CatalogImportService.key(s.menuItem()), s.menuItem());
        }
        if (servings.isEmpty()) return r;
        List<RecipeRow> recipes = r.recipes().stream().map(x -> {
            BigDecimal n = x.menuItem() == null ? null : servings.get(CatalogImportService.key(x.menuItem()));
            return n == null || x.quantity() == null ? x : new RecipeRow(x.row(), x.menuItem(), x.price(), x.section(),
                    x.ingredient(), x.quantity().divide(n, 6, java.math.RoundingMode.HALF_UP).stripTrailingZeros(), x.unit());
        }).toList();
        List<String> warnings = new ArrayList<>(r.warnings());
        servings.forEach((key, n) -> warnings.add("«" + named.get(key) + "» rinde " + n.stripTrailingZeros().toPlainString()
                + " porciones: puse las cantidades de una."));
        return new CatalogSpreadsheetInterpretation(r.products(), r.preparations(), recipes, r.proposedProducts(),
                r.ingredientMatches(), warnings);
    }

    /**
     * El nombre que ya existe y es el mismo salvo un plural ("huevos" → "Huevo", "limones" → "Limón"); null si no hay
     * uno solo. Con dos candidatos no se elige: es el nombre ambiguo que el import pide unificar.
     */
    static String singularMatch(String name, java.util.Collection<String> existing) {
        List<String> found = existing.stream().filter(e -> samePlural(name, e)).distinct().toList();
        return found.size() == 1 ? found.getFirst() : null;
    }

    /** "acelga" → "Acelga": solo la primera letra, el resto como vino. */
    static String capitalized(String name) {
        if (name == null || name.isBlank()) return null;
        String n = name.trim().replaceAll("\\s+", " ");
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    /** ponytail: plural español simple, palabra por palabra (+s, +es). "Nuez" y "nueces" no los iguala. */
    static boolean samePlural(String a, String b) {
        String[] x = CatalogImportService.key(a).split(" ");
        String[] y = CatalogImportService.key(b).split(" ");
        if (x.length != y.length) return false;
        for (int i = 0; i < x.length; i++) {
            String p = x[i], q = y[i];
            if (!(p.equals(q) || p.equals(q + "s") || p.equals(q + "es") || q.equals(p + "s") || q.equals(p + "es"))) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasData(SpreadsheetRow row) {
        return row.cells() != null
                && row.cells().stream().anyMatch(c -> c != null && !c.toString().isBlank());
    }
}
