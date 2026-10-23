package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.service.MenuItemService.CreateMenuItemCommand;
import com.chefcontrol.application.service.MenuItemService.RecipeItemCommand;
import com.chefcontrol.application.service.MenuItemService.SetRecipeCommand;
import com.chefcontrol.application.service.ProductService.CreateProductCommand;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.menu.MenuSection;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.product.ProductCategory;
import com.chefcontrol.domain.product.Unit;
import com.chefcontrol.domain.repository.MenuItemRepository;
import com.chefcontrol.domain.repository.MenuSectionRepository;
import com.chefcontrol.domain.repository.ProductCategoryRepository;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.RecipeRepository;
import com.chefcontrol.domain.repository.UnitRepository;
import com.chefcontrol.domain.shared.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Alta de insumos y recetas en lote. El front lee el Excel y manda las filas; acá no se sabe de Excel.
 *
 * <p><b>Todo o nada, y la validación va completa antes de escribir:</b> la auditoría es {@code @Async} en otra
 * transacción, así que si algo fallara a mitad de la escritura quedarían líneas de cosas que no se guardaron.
 * Por eso primero se valida todo sin tocar nada, y solo sin ningún error se escribe, en una transacción.
 *
 * <p>Se escribe a través de los servicios de siempre para no saltear lo que hacen: historial de rendimiento y
 * de precio, {@code recipe_versions} y la auditoría de cada alta.
 *
 * <p>Reglas (Mati, 2026-10-23): un insumo que ya existe no se toca (cambiarle la unidad rompería su stock); un
 * ingrediente tiene que existir o venir en la hoja de insumos, nunca se crea solo (la misma regla que la IA);
 * a un plato que ya existe se le reemplaza la receta pero no el precio ni el paso; sin costos ni stock.
 */
@Service
@RequiredArgsConstructor
public class CatalogImportService {

    public static final int MAX_PRODUCT_ROWS = 1000;
    public static final int MAX_RECIPE_ROWS = 3000;

    // Los anchos de las columnas: pasarlos haría fallar el INSERT a mitad de la escritura.
    private static final int MAX_NAME = 255;            // products.name, menu_items.name
    private static final int MAX_GROUP_NAME = 100;      // product_categories.name, menu_sections.name
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("1000000000");  // NUMERIC(12,3)
    private static final BigDecimal MAX_PRICE = new BigDecimal("10000000000");    // NUMERIC(12,2)
    private static final BigDecimal MAX_YIELD = new BigDecimal("1000");           // CHECK de V21
    // ponytail: el catálogo entero en memoria; un restaurante tiene cientos de insumos, no millones.
    private static final PageRequest ALL = PageRequest.of(0, Integer.MAX_VALUE);

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final UnitRepository unitRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuSectionRepository menuSectionRepository;
    private final RecipeRepository recipeRepository;
    private final UnitConversionService unitConversionService;
    private final ProductService productService;
    private final MenuSectionService menuSectionService;
    private final MenuItemService menuItemService;
    private final AuditService auditService;

    @Transactional
    public Report importCatalog(List<ProductRow> products, List<RecipeRow> recipes, boolean dryRun) {
        UUID restaurantId = TenantContext.require();
        Plan plan = new Plan(restaurantId);
        products.forEach(plan::addProduct);
        plan.addRecipes(recipes);

        boolean save = !dryRun && plan.errors.isEmpty();
        if (save) write(plan);
        return plan.report(save);
    }

    private void write(Plan plan) {
        Map<String, UUID> categoryIds = new HashMap<>(plan.categoryIds);
        plan.newCategories.forEach((key, name) ->
                categoryIds.put(key, productService.createCategory(name, null, null, null, null).getId()));

        Map<String, UUID> productIds = new HashMap<>(plan.productIds);
        plan.newProducts.forEach((key, p) -> productIds.put(key, productService.createProduct(new CreateProductCommand(
                p.name(), null, p.unitId(), p.categoryKey() == null ? null : categoryIds.get(p.categoryKey()),
                p.minStock(), null, p.yieldPercentage())).getId()));

        Map<String, UUID> sectionIds = new HashMap<>(plan.sectionIds);
        plan.newSections.forEach((key, name) ->
                sectionIds.put(key, menuSectionService.createSection(name, null, null).getId()));

        for (PlannedDish dish : plan.dishes) {
            UUID menuItemId = dish.existingId() != null ? dish.existingId()
                    : menuItemService.createMenuItem(new CreateMenuItemCommand(dish.name(), null, dish.price(),
                            dish.sectionKey() == null ? null : sectionIds.get(dish.sectionKey()))).getId();
            // Las cantidades de la hoja son para un plato.
            menuItemService.setRecipe(menuItemId, new SetRecipeCommand(1, dish.items().stream()
                    .map(i -> new RecipeItemCommand(productIds.get(i.productKey()), i.unitId(), i.quantity()))
                    .toList()));
        }

        Report counts = plan.report(true);
        if (counts.changedAnything()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("productsCreated", counts.productsCreated());
            payload.put("categoriesCreated", counts.categoriesCreated());
            payload.put("sectionsCreated", counts.sectionsCreated());
            payload.put("menuItemsCreated", counts.menuItemsCreated());
            payload.put("recipesCreated", counts.recipesCreated());
            payload.put("recipesReplaced", counts.recipesReplaced());
            auditService.log(AuditAction.CATALOG_IMPORTED, "Catalog", null, payload);
        }
    }

    /**
     * La comparación de nombres: sin espacios de más, sin mayúsculas y sin acentos. "Limón " = "limon".
     * Vale para insumos, ingredientes, categorías, pasos, platos y unidades.
     */
    static String key(String s) {
        String n = Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "").replaceAll("\\s+", " ");
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim().replaceAll("\\s+", " ");
    }

    /** Lo que se va a escribir, armado sin escribir nada. */
    private class Plan {
        final UUID restaurantId;
        final List<RowError> errors = new ArrayList<>();
        final Map<String, Unit> units = new HashMap<>();
        final Map<String, UUID> categoryIds = new HashMap<>();
        final Map<String, UUID> productIds = new HashMap<>();
        final Map<String, UUID> productUnits = new HashMap<>();    // existentes + nuevos: para los ingredientes
        final Map<String, MenuItem> existingDishes = new HashMap<>();
        final Map<String, UUID> sectionIds = new HashMap<>();
        final Map<List<UUID>, Boolean> convertible = new HashMap<>();

        final Set<String> sheetProducts = new HashSet<>();         // los nombres de la hoja de insumos
        final Set<String> invalidSheetProducts = new HashSet<>();  // los que tienen error: su ingrediente no se reporta dos veces
        final Map<String, String> newCategories = new LinkedHashMap<>();
        final Map<String, PlannedProduct> newProducts = new LinkedHashMap<>();
        final Map<String, String> newSections = new LinkedHashMap<>();
        final List<PlannedDish> dishes = new ArrayList<>();
        int productsExisting;

        Plan(UUID restaurantId) {
            this.restaurantId = restaurantId;
            List<Unit> all = unitRepository.findAll();
            all.forEach(u -> units.putIfAbsent(key(u.getName()), u));
            all.stream().filter(u -> u.getAbbreviation() != null)
                    .forEach(u -> units.putIfAbsent(key(u.getAbbreviation()), u));
            for (ProductCategory c : categoryRepository.findAllAccessibleToRestaurant(restaurantId)) {
                categoryIds.putIfAbsent(key(c.getName()), c.getId());
            }
            for (Product p : productRepository.findByRestaurantIdAndIsActiveTrue(restaurantId, ALL).content()) {
                productIds.putIfAbsent(key(p.getName()), p.getId());
                productUnits.putIfAbsent(key(p.getName()), p.getDefaultUnitId());
            }
            for (MenuItem m : menuItemRepository.findByRestaurantIdAndActive(restaurantId, true, ALL).content()) {
                existingDishes.putIfAbsent(key(m.getName()), m);
            }
            for (MenuSection s : menuSectionRepository.findAllByRestaurantId(restaurantId)) {
                sectionIds.putIfAbsent(key(s.getName()), s.getId());
            }
        }

        void addProduct(ProductRow r) {
            int before = errors.size();
            String name = clean(r.name());
            String key = name == null ? null : key(name);
            if (name == null) {
                error(Sheet.PRODUCTS, r.row(), "name", ErrorCode.IMPORT_REQUIRED_FIELD, null);
            } else if (name.length() > MAX_NAME) {
                error(Sheet.PRODUCTS, r.row(), "name", ErrorCode.IMPORT_INVALID_VALUE, name);
            } else if (!sheetProducts.add(key)) {
                error(Sheet.PRODUCTS, r.row(), "name", ErrorCode.IMPORT_DUPLICATE_PRODUCT, name);
                return;
            }
            Unit unit = unit(Sheet.PRODUCTS, r.row(), r.unit());
            String category = clean(r.category());
            if (category != null && category.length() > MAX_GROUP_NAME) {
                error(Sheet.PRODUCTS, r.row(), "category", ErrorCode.IMPORT_INVALID_VALUE, category);
            }
            if (r.minStock() != null && (r.minStock().signum() < 0 || r.minStock().compareTo(MAX_QUANTITY) >= 0)) {
                error(Sheet.PRODUCTS, r.row(), "minStock", ErrorCode.IMPORT_INVALID_VALUE, r.minStock());
            }
            if (r.yieldPercentage() != null
                    && (r.yieldPercentage().signum() <= 0 || r.yieldPercentage().compareTo(MAX_YIELD) > 0)) {
                error(Sheet.PRODUCTS, r.row(), "yieldPercentage", ErrorCode.IMPORT_INVALID_VALUE, r.yieldPercentage());
            }

            if (key == null) return;
            if (errors.size() > before) {
                invalidSheetProducts.add(key);
                return;
            }
            if (productIds.containsKey(key)) {
                productsExisting++;
                return;
            }
            String categoryKey = category == null ? null : key(category);
            if (categoryKey != null && !categoryIds.containsKey(categoryKey)) {
                newCategories.putIfAbsent(categoryKey, category);
            }
            newProducts.put(key, new PlannedProduct(name, unit.getId(), categoryKey, r.minStock(), r.yieldPercentage()));
            productUnits.put(key, unit.getId());
        }

        /** Una fila por ingrediente: las filas del mismo plato (por nombre comparado) forman su receta. */
        void addRecipes(List<RecipeRow> rows) {
            Map<String, List<RecipeRow>> byDish = new LinkedHashMap<>();
            for (RecipeRow r : rows) {
                String dish = clean(r.menuItem());
                if (dish == null) {
                    error(Sheet.RECIPES, r.row(), "menuItem", ErrorCode.IMPORT_REQUIRED_FIELD, null);
                } else {
                    byDish.computeIfAbsent(key(dish), k -> new ArrayList<>()).add(r);
                }
            }
            byDish.forEach(this::addDish);
        }

        private void addDish(String dishKey, List<RecipeRow> rows) {
            int before = errors.size();
            RecipeRow first = rows.getFirst();
            String name = clean(first.menuItem());
            MenuItem existing = existingDishes.get(dishKey);
            if (existing == null && name.length() > MAX_NAME) {
                error(Sheet.RECIPES, first.row(), "menuItem", ErrorCode.IMPORT_INVALID_VALUE, name);
            }

            // Precio y paso: los de la primera fila que los traiga. Solo se usan si el plato es nuevo.
            BigDecimal price = null;
            String section = null;
            Set<String> ingredients = new HashSet<>();
            List<PlannedIngredient> items = new ArrayList<>();
            for (RecipeRow r : rows) {
                if (r.price() != null) {
                    if (r.price().signum() <= 0 || r.price().compareTo(MAX_PRICE) >= 0) {
                        error(Sheet.RECIPES, r.row(), "price", ErrorCode.IMPORT_INVALID_VALUE, r.price());
                    } else if (price == null) {
                        price = r.price();
                    }
                }
                String sec = clean(r.section());
                if (sec != null && sec.length() > MAX_GROUP_NAME) {
                    error(Sheet.RECIPES, r.row(), "section", ErrorCode.IMPORT_INVALID_VALUE, sec);
                } else if (sec != null && section == null) {
                    section = sec;
                }
                PlannedIngredient item = ingredient(r, ingredients);
                if (item != null) items.add(item);
            }
            if (errors.size() > before) return;

            String sectionKey = null;
            if (existing == null && section != null) {
                sectionKey = key(section);
                if (!sectionIds.containsKey(sectionKey)) newSections.putIfAbsent(sectionKey, section);
            }
            boolean hasRecipe = existing != null
                    && recipeRepository.findByMenuItemIdAndRestaurantId(existing.getId(), restaurantId).isPresent();
            dishes.add(new PlannedDish(name, existing == null ? null : existing.getId(), price, sectionKey,
                    hasRecipe, items));
        }

        private PlannedIngredient ingredient(RecipeRow r, Set<String> seenInDish) {
            int before = errors.size();
            String name = clean(r.ingredient());
            String key = name == null ? null : key(name);
            UUID productUnit = null;
            if (name == null) {
                error(Sheet.RECIPES, r.row(), "ingredient", ErrorCode.IMPORT_REQUIRED_FIELD, null);
            } else if (!seenInDish.add(key)) {
                error(Sheet.RECIPES, r.row(), "ingredient", ErrorCode.IMPORT_DUPLICATE_INGREDIENT, name);
            } else if (productUnits.containsKey(key)) {
                productUnit = productUnits.get(key);
            } else if (!invalidSheetProducts.contains(key)) {
                // Nunca se crea solo: un "tomates" de más arruina el food cost en silencio (la misma regla que la IA).
                error(Sheet.RECIPES, r.row(), "ingredient", ErrorCode.IMPORT_UNKNOWN_INGREDIENT, name);
            }
            if (r.quantity() == null) {
                error(Sheet.RECIPES, r.row(), "quantity", ErrorCode.IMPORT_REQUIRED_FIELD, null);
            } else if (r.quantity().signum() <= 0 || r.quantity().compareTo(MAX_QUANTITY) >= 0) {
                error(Sheet.RECIPES, r.row(), "quantity", ErrorCode.IMPORT_INVALID_VALUE, r.quantity());
            }
            Unit unit = unit(Sheet.RECIPES, r.row(), r.unit());
            if (unit != null && productUnit != null && !convertible(unit.getId(), productUnit)) {
                error(Sheet.RECIPES, r.row(), "unit", ErrorCode.IMPORT_INCOMPATIBLE_UNIT, r.unit());
            }
            return errors.size() > before || productUnit == null ? null
                    : new PlannedIngredient(key, unit.getId(), r.quantity());
        }

        private Unit unit(Sheet sheet, int row, String raw) {
            String name = clean(raw);
            if (name == null) {
                error(sheet, row, "unit", ErrorCode.IMPORT_REQUIRED_FIELD, null);
                return null;
            }
            Unit unit = units.get(key(name));
            if (unit == null) error(sheet, row, "unit", ErrorCode.IMPORT_UNKNOWN_UNIT, name);
            return unit;
        }

        /** La unidad de la receta tiene que convertir a la del insumo: el descuento de stock la convierte. */
        private boolean convertible(UUID from, UUID to) {
            return from.equals(to) || convertible.computeIfAbsent(List.of(from, to), k -> {
                try {
                    unitConversionService.convert(BigDecimal.ONE, from, to);
                    return true;
                } catch (AppException e) {
                    return false;
                }
            });
        }

        private void error(Sheet sheet, int row, String field, ErrorCode code, Object value) {
            String text = value instanceof BigDecimal n ? n.toPlainString() : value == null ? null : value.toString();
            errors.add(new RowError(sheet, row, field, code, text));
        }

        Report report(boolean saved) {
            List<RowError> sorted = new ArrayList<>(errors);
            sorted.sort(Comparator.comparing(RowError::sheet).thenComparingInt(RowError::row));
            return new Report(saved,
                    newProducts.size(),
                    productsExisting,
                    newCategories.size(),
                    newSections.size(),
                    (int) dishes.stream().filter(d -> d.existingId() == null).count(),
                    (int) dishes.stream().filter(d -> !d.hasRecipe()).count(),
                    (int) dishes.stream().filter(PlannedDish::hasRecipe).count(),
                    sorted);
        }
    }

    // ── Commands y resultado ────────────────────────────────────────────────

    /** @param row el número de fila del Excel, para los mensajes */
    public record ProductRow(int row, String name, String unit, String category,
                             BigDecimal minStock, BigDecimal yieldPercentage) {}

    /** Una fila por ingrediente. {@code quantity} es para un plato. */
    public record RecipeRow(int row, String menuItem, BigDecimal price, String section,
                            String ingredient, BigDecimal quantity, String unit) {}

    public enum Sheet { PRODUCTS, RECIPES }

    /** @param field el campo de la fila (name, unit, quantity…); {@code value} es lo que vino, null si faltaba */
    public record RowError(Sheet sheet, int row, String field, ErrorCode errorCode, String value) {}

    /** Con errores, o con {@code dryRun}, {@code saved} es false y los contadores dicen lo que se haría. */
    public record Report(boolean saved, int productsCreated, int productsExisting, int categoriesCreated,
                         int sectionsCreated, int menuItemsCreated, int recipesCreated, int recipesReplaced,
                         List<RowError> errors) {
        boolean changedAnything() {
            return productsCreated + categoriesCreated + sectionsCreated + menuItemsCreated
                    + recipesCreated + recipesReplaced > 0;
        }
    }

    private record PlannedProduct(String name, UUID unitId, String categoryKey,
                                  BigDecimal minStock, BigDecimal yieldPercentage) {}

    private record PlannedIngredient(String productKey, UUID unitId, BigDecimal quantity) {}

    private record PlannedDish(String name, UUID existingId, BigDecimal price, String sectionKey,
                               boolean hasRecipe, List<PlannedIngredient> items) {}
}
