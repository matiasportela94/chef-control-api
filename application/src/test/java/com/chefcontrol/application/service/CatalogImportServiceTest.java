package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.application.service.CatalogImportService.ProductRow;
import com.chefcontrol.application.service.CatalogImportService.RecipeRow;
import com.chefcontrol.application.service.CatalogImportService.Report;
import com.chefcontrol.application.service.CatalogImportService.RowError;
import com.chefcontrol.application.service.CatalogImportService.Sheet;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.menu.MenuSection;
import com.chefcontrol.domain.menu.Recipe;
import com.chefcontrol.domain.menu.RecipeItem;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.product.ProductCategory;
import com.chefcontrol.domain.product.Unit;
import com.chefcontrol.domain.product.UnitType;
import com.chefcontrol.domain.repository.*;
import com.chefcontrol.domain.shared.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Con los servicios reales (producto, paso, plato, receta) sobre repositorios en memoria: así se ve que el import
 * deja lo mismo que el alta a mano —historial, recipe_versions, auditoría— y que con un error no escribe nada.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogImportServiceTest {

    @Mock ProductRepository productRepository;
    @Mock ProductCategoryRepository categoryRepository;
    @Mock UnitRepository unitRepository;
    @Mock UnitConversionRepository unitConversionRepository;
    @Mock MenuItemRepository menuItemRepository;
    @Mock MenuSectionRepository menuSectionRepository;
    @Mock RecipeRepository recipeRepository;
    @Mock RecipeVersionRepository recipeVersionRepository;
    @Mock PriceHistoryRepository priceHistoryRepository;
    @Mock CartaRepository cartaRepository;
    @Mock AuditService auditService;
    @Mock CurrentUserProvider currentUserProvider;

    private static final UUID R = UUID.randomUUID();
    private final Unit g = unit("Gramo", "g", UnitType.WEIGHT, "1");
    private final Unit kg = unit("Kilogramo", "kg", UnitType.WEIGHT, "1000");
    private final Unit litro = unit("Litro", "l", UnitType.VOLUME, "1000");
    private final Unit unidad = unit("Unidad", "u", UnitType.UNIT, "1");

    private final Map<UUID, Product> products = new LinkedHashMap<>();
    private final List<ProductCategory> categories = new ArrayList<>();
    private final List<MenuSection> sections = new ArrayList<>();
    private final Map<UUID, MenuItem> menuItems = new LinkedHashMap<>();
    private final Map<UUID, Recipe> recipes = new HashMap<>();

    private Product limon;
    private MenuItem milanesa;
    private CatalogImportService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(R);
        List<Unit> units = List.of(g, kg, litro, unidad);
        when(unitRepository.findAll()).thenReturn(units);
        when(unitRepository.findById(any())).thenAnswer(i -> units.stream().filter(u -> u.getId().equals(i.getArgument(0))).findFirst());

        when(categoryRepository.findAllAccessibleToRestaurant(R)).thenReturn(categories);
        when(categoryRepository.findByIdAccessibleTo(any(), eq(R))).thenAnswer(i -> categories.stream().filter(c -> c.getId().equals(i.getArgument(0))).findFirst());
        when(categoryRepository.save(any())).thenAnswer(i -> { ProductCategory c = i.getArgument(0); c.setId(UUID.randomUUID()); categories.add(c); return c; });

        when(productRepository.findByRestaurantIdAndIsActiveTrue(eq(R), any())).thenAnswer(i -> page(products.values().stream().filter(Product::isActive).toList()));
        when(productRepository.findByIdAndRestaurantId(any(), eq(R))).thenAnswer(i -> Optional.ofNullable(products.get(i.<UUID>getArgument(0))));
        when(productRepository.save(any())).thenAnswer(i -> { Product p = i.getArgument(0); if (p.getId() == null) p.setId(UUID.randomUUID()); products.put(p.getId(), p); return p; });

        when(menuSectionRepository.findAllByRestaurantId(R)).thenReturn(sections);
        when(menuSectionRepository.findByIdAndRestaurantId(any(), eq(R))).thenAnswer(i -> sections.stream().filter(s -> s.getId().equals(i.getArgument(0))).findFirst());
        when(menuSectionRepository.save(any())).thenAnswer(i -> { MenuSection s = i.getArgument(0); s.setId(UUID.randomUUID()); sections.add(s); return s; });

        when(menuItemRepository.findByRestaurantIdAndActive(eq(R), eq(true), any())).thenAnswer(i -> page(menuItems.values().stream().filter(MenuItem::isActive).toList()));
        when(menuItemRepository.findByIdAndRestaurantId(any(), eq(R))).thenAnswer(i -> Optional.ofNullable(menuItems.get(i.<UUID>getArgument(0))));
        when(menuItemRepository.save(any())).thenAnswer(i -> { MenuItem m = i.getArgument(0); if (m.getId() == null) m.setId(UUID.randomUUID()); menuItems.put(m.getId(), m); return m; });

        when(recipeRepository.findByMenuItemIdAndRestaurantId(any(), eq(R))).thenAnswer(i -> Optional.ofNullable(recipes.get(i.<UUID>getArgument(0))));
        when(recipeRepository.save(any())).thenAnswer(i -> { Recipe r = i.getArgument(0); if (r.getId() == null) r.setId(UUID.randomUUID()); recipes.put(r.getMenuItemId(), r); return r; });
        when(recipeVersionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Catálogo que ya existe: limón (kg), verduras, principales y una milanesa con receta para 2.
        limon = new Product();
        limon.setId(UUID.randomUUID());
        limon.setRestaurantId(R);
        limon.setName("Limón");
        limon.setDefaultUnitId(kg.getId());
        limon.setYieldPercentage(new BigDecimal("100"));
        products.put(limon.getId(), limon);
        ProductCategory verduras = new ProductCategory();
        verduras.setId(UUID.randomUUID());
        verduras.setName("Verduras");
        categories.add(verduras);
        sections.add(MenuSection.builder().id(UUID.randomUUID()).restaurantId(R).name("Principales").build());
        milanesa = MenuItem.builder().id(UUID.randomUUID()).restaurantId(R).name("Milanesa").price(new BigDecimal("9000")).active(true).build();
        menuItems.put(milanesa.getId(), milanesa);
        Recipe old = new Recipe();
        old.setId(UUID.randomUUID());
        old.setMenuItemId(milanesa.getId());
        old.setRestaurantId(R);
        old.setServings(2);
        old.replaceItems(List.of(RecipeItem.builder().productId(limon.getId()).unitId(kg.getId()).quantity(new BigDecimal("0.2")).build()));
        recipes.put(milanesa.getId(), old);

        ProductService productService = new ProductService(productRepository, categoryRepository, unitRepository,
                auditService, priceHistoryRepository, currentUserProvider);
        MenuSectionService sectionService = new MenuSectionService(menuSectionRepository, menuItemRepository, auditService);
        MenuItemService menuItemService = new MenuItemService(menuItemRepository, cartaRepository, menuSectionRepository,
                recipeRepository, productRepository, unitRepository, auditService, priceHistoryRepository,
                recipeVersionRepository, currentUserProvider);
        service = new CatalogImportService(productRepository, categoryRepository, unitRepository, menuItemRepository,
                menuSectionRepository, recipeRepository, new UnitConversionService(unitConversionRepository, unitRepository),
                productService, sectionService, menuItemService, auditService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void namesAreComparedWithoutCaseAccentsOrExtraSpaces() {
        assertEquals("limon tahiti", CatalogImportService.key("  Limón   TAHITÍ "));

        Report report = service.importCatalog(
                List.of(new ProductRow(2, " LIMON ", "KG", null, null, null)),
                List.of(new RecipeRow(2, "Limonada", new BigDecimal("1500"), null, "limón", new BigDecimal("50"), "Gramo")),
                false);

        assertTrue(report.errors().isEmpty(), () -> report.errors().toString());
        assertEquals(1, report.productsExisting());
        assertEquals(0, report.productsCreated());
        Recipe recipe = recipes.get(findMenuItem("Limonada").getId());
        assertEquals(limon.getId(), recipe.getItems().getFirst().getProductId());
        assertEquals(g.getId(), recipe.getItems().getFirst().getUnitId());
    }

    @Test
    void oneBadRowAndNothingIsSaved() {
        Report report = service.importCatalog(
                List.of(new ProductRow(2, "Papa", "kg", "Tubérculos", null, null)),
                List.of(new RecipeRow(2, "Ñoquis", new BigDecimal("8000"), "Pastas", "papa", new BigDecimal("200"), "g"),
                        new RecipeRow(3, "Ñoquis", null, null, "harina 0000", new BigDecimal("50"), "g")),
                false);

        assertFalse(report.saved());
        assertEquals(List.of(new RowError(Sheet.RECIPES, 3, "ingredient", ErrorCode.IMPORT_UNKNOWN_INGREDIENT, "harina 0000")),
                report.errors());
        assertNothingWritten();
    }

    @Test
    void dryRunCountsWithoutWritingOrAuditing() {
        Report report = service.importCatalog(
                List.of(new ProductRow(2, "Papa", "kg", "Tubérculos", null, null)),
                List.of(new RecipeRow(2, "Ñoquis", new BigDecimal("8000"), "Pastas", "papa", new BigDecimal("200"), "g"),
                        new RecipeRow(3, "Milanesa", null, null, "limón", new BigDecimal("10"), "g")),
                true);

        assertFalse(report.saved());
        assertTrue(report.errors().isEmpty());
        assertEquals(1, report.productsCreated());
        assertEquals(1, report.categoriesCreated());
        assertEquals(1, report.sectionsCreated());
        assertEquals(1, report.menuItemsCreated());
        assertEquals(1, report.recipesCreated());
        assertEquals(1, report.recipesReplaced());
        assertNothingWritten();
    }

    @Test
    void existingProductIsLeftUntouched() {
        Report report = service.importCatalog(
                List.of(new ProductRow(2, "limon", "g", "Cítricos", new BigDecimal("5"), new BigDecimal("40"))), List.of(), false);

        assertTrue(report.saved());
        assertEquals(1, report.productsExisting());
        assertEquals(0, report.categoriesCreated()); // la categoría de un insumo que no se crea no se crea
        assertEquals(kg.getId(), limon.getDefaultUnitId());
        assertEquals(0, new BigDecimal("100").compareTo(limon.getYieldPercentage()));
        verify(productRepository, never()).save(any());
        verify(categoryRepository, never()).save(any());
        verifyNoInteractions(auditService); // nada cambió: ni la línea resumen
    }

    @Test
    void missingCategoriesAndSectionsAreCreatedOnce() {
        Report report = service.importCatalog(
                List.of(new ProductRow(2, "Papa", "kg", "Tubérculos", null, new BigDecimal("90")),
                        new ProductRow(3, "Batata", "kg", "tuberculos", null, null),
                        new ProductRow(4, "Lechuga", "u", "verduras", null, null)),
                List.of(new RecipeRow(2, "Ñoquis", new BigDecimal("8000"), "Pastas", "papa", new BigDecimal("200"), "g"),
                        new RecipeRow(3, "Sorrentinos", null, "pastas", "batata", new BigDecimal("150"), "g"),
                        new RecipeRow(4, "Ensalada", null, "PRINCIPALES", "lechuga", new BigDecimal("1"), "u")),
                false);

        assertTrue(report.saved(), () -> report.errors().toString());
        assertEquals(1, report.categoriesCreated());
        assertEquals(1, report.sectionsCreated());
        verify(categoryRepository, times(1)).save(any());
        verify(menuSectionRepository, times(1)).save(any());

        ProductCategory tuberculos = categories.stream().filter(c -> c.getName().equals("Tubérculos")).findFirst().orElseThrow();
        assertEquals(tuberculos.getId(), findProduct("Batata").getCategoryId());
        assertEquals(categories.getFirst().getId(), findProduct("Lechuga").getCategoryId()); // "verduras" = Verduras
        assertEquals(findMenuItem("Ñoquis").getSectionId(), findMenuItem("Sorrentinos").getSectionId());
        assertEquals(sections.getFirst().getId(), findMenuItem("Ensalada").getSectionId());
        assertEquals(new BigDecimal("8000"), findMenuItem("Ñoquis").getPrice());
        verify(priceHistoryRepository).saveProductYield(argThat(e -> e.value().compareTo(new BigDecimal("90")) == 0));
        verify(auditService).log(eq(AuditAction.CATALOG_IMPORTED), eq("Catalog"), isNull(), anyMap());
    }

    @Test
    void existingDishGetsItsRecipeReplacedWithANewVersionButKeepsPriceAndSection() {
        Report report = service.importCatalog(List.of(),
                List.of(new RecipeRow(2, "milanesa", new BigDecimal("1"), "Postres", "Limón", new BigDecimal("30"), "g")),
                false);

        assertTrue(report.saved(), () -> report.errors().toString());
        assertEquals(1, report.recipesReplaced());
        assertEquals(0, report.menuItemsCreated());
        assertEquals(0, report.sectionsCreated());
        Recipe recipe = recipes.get(milanesa.getId());
        assertEquals(1, recipe.getServings());
        assertEquals(1, recipe.getItems().size());
        assertEquals(new BigDecimal("30"), recipe.getItems().getFirst().getQuantity());
        assertEquals(new BigDecimal("9000"), milanesa.getPrice());
        assertNull(milanesa.getSectionId());
        verify(menuItemRepository, never()).save(any());
        verify(recipeVersionRepository).save(any());
        verify(auditService).log(eq(AuditAction.RECIPE_UPDATED), eq("Recipe"), any(), anyMap());
    }

    @Test
    void unitThatDoesNotConvertToTheProductsUnitIsRejected() {
        Report report = service.importCatalog(List.of(),
                List.of(new RecipeRow(5, "Limonada", null, null, "limón", new BigDecimal("1"), "Litro")), false);

        assertEquals(List.of(new RowError(Sheet.RECIPES, 5, "unit", ErrorCode.IMPORT_INCOMPATIBLE_UNIT, "Litro")),
                report.errors());
        assertNothingWritten();
    }

    @Test
    void duplicatesAreReported() {
        Report report = service.importCatalog(
                List.of(new ProductRow(2, "Papa", "kg", null, null, null),
                        new ProductRow(3, "papá ", "kg", null, null, null)),
                List.of(new RecipeRow(2, "Ñoquis", null, null, "papa", new BigDecimal("100"), "g"),
                        new RecipeRow(3, "ñoquis", null, null, "Papa", new BigDecimal("50"), "g")),
                false);

        assertEquals(List.of(
                new RowError(Sheet.PRODUCTS, 3, "name", ErrorCode.IMPORT_DUPLICATE_PRODUCT, "papá"),
                new RowError(Sheet.RECIPES, 3, "ingredient", ErrorCode.IMPORT_DUPLICATE_INGREDIENT, "Papa")),
                report.errors());
        assertNothingWritten();
    }

    @Test
    void missingAndInvalidFieldsSayWhichField() {
        Report report = service.importCatalog(
                List.of(new ProductRow(2, "Papa", null, null, null, new BigDecimal("0")),
                        new ProductRow(3, "Sal", "pizca", null, new BigDecimal("-1"), null)),
                List.of(new RecipeRow(2, "Ñoquis", new BigDecimal("-5"), null, "papa", new BigDecimal("0"), "g"),
                        new RecipeRow(3, "", null, null, "sal", null, "g")),
                false);

        assertEquals(List.of(
                new RowError(Sheet.PRODUCTS, 2, "unit", ErrorCode.IMPORT_REQUIRED_FIELD, null),
                new RowError(Sheet.PRODUCTS, 2, "yieldPercentage", ErrorCode.IMPORT_INVALID_VALUE, "0"),
                new RowError(Sheet.PRODUCTS, 3, "unit", ErrorCode.IMPORT_UNKNOWN_UNIT, "pizca"),
                new RowError(Sheet.PRODUCTS, 3, "minStock", ErrorCode.IMPORT_INVALID_VALUE, "-1"),
                // "papa" vino con error en la hoja de insumos: no se repite como ingrediente desconocido
                new RowError(Sheet.RECIPES, 2, "price", ErrorCode.IMPORT_INVALID_VALUE, "-5"),
                new RowError(Sheet.RECIPES, 2, "quantity", ErrorCode.IMPORT_INVALID_VALUE, "0"),
                new RowError(Sheet.RECIPES, 3, "menuItem", ErrorCode.IMPORT_REQUIRED_FIELD, null)),
                report.errors());
        assertNothingWritten();
    }

    private void assertNothingWritten() {
        verify(productRepository, never()).save(any());
        verify(categoryRepository, never()).save(any());
        verify(menuSectionRepository, never()).save(any());
        verify(menuItemRepository, never()).save(any());
        verify(recipeRepository, never()).save(any());
        verifyNoInteractions(recipeVersionRepository, priceHistoryRepository, auditService);
    }

    private Product findProduct(String name) {
        return products.values().stream().filter(p -> p.getName().equals(name)).findFirst().orElseThrow();
    }

    private MenuItem findMenuItem(String name) {
        return menuItems.values().stream().filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
    }

    private static <T> Page<T> page(List<T> content) {
        return new Page<>(content, 0, content.size(), content.size());
    }

    private static Unit unit(String name, String abbreviation, UnitType type, String factor) {
        Unit u = new Unit();
        u.setId(UUID.randomUUID());
        u.setName(name);
        u.setAbbreviation(abbreviation);
        u.setType(type);
        u.setToBaseFactor(new BigDecimal(factor));
        return u;
    }
}
