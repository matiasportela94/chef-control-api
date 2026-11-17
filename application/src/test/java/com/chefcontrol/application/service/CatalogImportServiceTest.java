package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.application.service.CatalogImportService.PreparationRow;
import com.chefcontrol.application.service.CatalogImportService.ProductRow;
import com.chefcontrol.application.service.CatalogImportService.RecipeReplacement;
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
import com.chefcontrol.domain.user.Permission;
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
    private static final Set<Permission> ALL = EnumSet.allOf(Permission.class);
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
        // Platos por menuItemId y preparaciones por productId: los dos dueños posibles de una receta.
        when(recipeRepository.save(any())).thenAnswer(i -> { Recipe r = i.getArgument(0); if (r.getId() == null) r.setId(UUID.randomUUID()); recipes.put(r.isPreparation() ? r.getProductId() : r.getMenuItemId(), r); return r; });
        when(recipeRepository.findByProductIdAndRestaurantId(any(), eq(R))).thenAnswer(i -> Optional.ofNullable(recipes.get(i.<UUID>getArgument(0))).filter(Recipe::isPreparation));
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
        UnitConversionService conversion = new UnitConversionService(unitConversionRepository, unitRepository);
        MenuItemService menuItemService = new MenuItemService(menuItemRepository, cartaRepository, menuSectionRepository,
                recipeRepository, productRepository, unitRepository, auditService, priceHistoryRepository,
                recipeVersionRepository, currentUserProvider, conversion);
        service = new CatalogImportService(productRepository, categoryRepository, unitRepository, menuItemRepository,
                menuSectionRepository, recipeRepository, conversion, productService, sectionService, menuItemService,
                auditService, new PreparationService(productRepository, recipeRepository, recipeVersionRepository,
                        unitRepository, conversion, currentUserProvider, auditService));
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
                false, ALL, Set.of());

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
                false, ALL, Set.of());

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
                true, ALL, Set.of());

        assertFalse(report.saved());
        assertTrue(report.errors().isEmpty());
        assertEquals(1, report.productsCreated());
        assertEquals(1, report.categoriesCreated());
        assertEquals(1, report.sectionsCreated());
        assertEquals(1, report.menuItemsCreated());
        assertEquals(1, report.recipesCreated());
        assertEquals(0, report.recipesReplaced());
        assertEquals(1, report.recipesSkipped());
        assertEquals(List.of(new RecipeReplacement(milanesa.getId(), "Milanesa", 3)), report.recipesToReplace());
        assertNothingWritten();
    }

    @Test
    void existingProductIsLeftUntouched() {
        Report report = service.importCatalog(
                List.of(new ProductRow(2, "limon", "g", "Cítricos", new BigDecimal("5"), new BigDecimal("40"))), List.of(), false, ALL, Set.of());

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
    void theIvaRateIsValidatedAndOnlySetOnNewProducts() {
        Report invalid = service.importCatalog(
                List.of(new ProductRow(2, "Papa", "kg", null, null, null, new BigDecimal("19"))), List.of(), false, ALL, Set.of());

        assertEquals(List.of(new RowError(Sheet.PRODUCTS, 2, "ivaPercentage", ErrorCode.IMPORT_INVALID_VALUE, "19")),
                invalid.errors());
        assertNothingWritten();

        Report report = service.importCatalog(
                List.of(new ProductRow(2, "Papa", "kg", null, null, null, new BigDecimal("10.5")),
                        new ProductRow(3, "limon", "kg", null, null, null, new BigDecimal("21"))), List.of(), false, ALL, Set.of());

        assertTrue(report.saved(), () -> report.errors().toString());
        assertEquals(0, new BigDecimal("10.5").compareTo(findProduct("Papa").getIvaPercentage()));
        assertNull(limon.getIvaPercentage()); // el que ya existe no se toca, tampoco el IVA
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
                false, ALL, Set.of());

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
                false, ALL, Set.of(milanesa.getId()));

        assertTrue(report.saved(), () -> report.errors().toString());
        assertEquals(1, report.recipesReplaced());
        assertEquals(0, report.recipesSkipped());
        assertEquals(List.of(new RecipeReplacement(milanesa.getId(), "Milanesa", 2)), report.recipesToReplace());
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
    void withoutItsIdAnExistingRecipeIsSkippedWithoutErrorAndListed() {
        Report report = service.importCatalog(List.of(),
                List.of(new RecipeRow(4, "milanesa", null, null, "Limón", new BigDecimal("30"), "g"),
                        new RecipeRow(5, "Limonada", null, null, "limón", new BigDecimal("50"), "g")),
                false, ALL, Set.of());

        assertTrue(report.saved(), () -> report.errors().toString());
        assertEquals(0, report.recipesReplaced());
        assertEquals(1, report.recipesSkipped());
        assertEquals(1, report.recipesCreated());
        assertEquals(List.of(new RecipeReplacement(milanesa.getId(), "Milanesa", 4)), report.recipesToReplace());
        Recipe kept = recipes.get(milanesa.getId());
        assertEquals(2, kept.getServings());
        assertEquals(new BigDecimal("0.2"), kept.getItems().getFirst().getQuantity());
        verify(recipeVersionRepository, times(1)).save(any()); // solo la de la Limonada
    }

    @Test
    void theSameRecipeIsANoOpEvenIfItsIdCame() {
        Recipe same = recipes.get(milanesa.getId());
        same.setServings(1);
        same.replaceItems(List.of(RecipeItem.builder().productId(limon.getId()).unitId(kg.getId()).quantity(new BigDecimal("0.200")).build()));

        Report report = service.importCatalog(List.of(),
                List.of(new RecipeRow(2, "Milanesa", null, null, "limon", new BigDecimal("0.2"), "kg")),
                false, ALL, Set.of(milanesa.getId()));

        assertTrue(report.saved(), () -> report.errors().toString());
        assertEquals(1, report.recipesUnchanged());
        assertEquals(0, report.recipesReplaced());
        assertEquals(List.of(), report.recipesToReplace());
        assertNothingWritten(); // ni receta, ni recipe_version, ni CATALOG_IMPORTED
    }

    @Test
    void sameItemsInAnotherUnitOrWithMoreServingsIsAChange() {
        // 200 g contra 0,2 kg guardado: misma cantidad, otra unidad
        Recipe saved = recipes.get(milanesa.getId());
        saved.setServings(1);
        Report otherUnit = service.importCatalog(List.of(),
                List.of(new RecipeRow(2, "Milanesa", null, null, "limon", new BigDecimal("200"), "g")), true, ALL, Set.of());
        assertEquals(1, otherUnit.recipesSkipped());

        // la guardada es para 2 porciones: el import guarda servings 1, así que cambia aunque los ítems sean iguales
        saved.setServings(2);
        Report moreServings = service.importCatalog(List.of(),
                List.of(new RecipeRow(2, "Milanesa", null, null, "limon", new BigDecimal("0.2"), "kg")), true, ALL, Set.of());
        assertEquals(1, moreServings.recipesSkipped());
        assertEquals(0, moreServings.recipesUnchanged());
    }

    @Test
    void anExistingDishWithoutARecipeGetsOneWithoutAsking() {
        MenuItem flan = MenuItem.builder().id(UUID.randomUUID()).restaurantId(R).name("Flan").price(new BigDecimal("3000")).active(true).build();
        menuItems.put(flan.getId(), flan);

        Report report = service.importCatalog(List.of(),
                List.of(new RecipeRow(2, "flan", null, null, "limón", new BigDecimal("10"), "g")), false, ALL, Set.of());

        assertTrue(report.saved(), () -> report.errors().toString());
        assertEquals(1, report.recipesCreated());
        assertEquals(List.of(), report.recipesToReplace());
        assertNotNull(recipes.get(flan.getId()));
    }

    @Test
    void aDishWithErrorsIsNotAskedAbout() {
        Report report = service.importCatalog(List.of(),
                List.of(new RecipeRow(2, "Milanesa", null, null, "pan rallado", new BigDecimal("30"), "g")), true, ALL, Set.of());

        assertEquals(List.of(new RowError(Sheet.RECIPES, 2, "ingredient", ErrorCode.IMPORT_UNKNOWN_INGREDIENT, "pan rallado")),
                report.errors());
        assertEquals(List.of(), report.recipesToReplace());
    }

    @Test
    void aPreparationOfTheSheetIsCreatedAsAProductWithItsRecipeAndADishCanUseIt() {
        Report report = service.importCatalog(List.of(),
                List.of(new PreparationRow(2, "Salsa de limón", new BigDecimal("500"), "g", "limón", new BigDecimal("0.2"), "kg")),
                List.of(new RecipeRow(5, "Ensalada", new BigDecimal("4000"), null, "salsa de limon", new BigDecimal("30"), "g")),
                false, ALL, Set.of(), Set.of());

        assertTrue(report.saved(), () -> report.errors().toString());
        assertEquals(1, report.preparationsCreated());
        assertEquals(1, report.productsCreated());
        Product salsa = products.values().stream().filter(p -> p.getName().equals("Salsa de limón")).findFirst().orElseThrow();
        assertEquals(g.getId(), salsa.getDefaultUnitId(), "the new product takes the yield's unit");
        Recipe prep = recipes.get(salsa.getId());
        assertEquals(0, new BigDecimal("500").compareTo(prep.getYieldQuantity()));
        assertEquals(limon.getId(), prep.getItems().getFirst().getProductId());
        assertEquals(salsa.getId(), recipes.get(findMenuItem("Ensalada").getId()).getItems().getFirst().getProductId());
        verify(auditService).log(eq(AuditAction.PRODUCT_RECIPE_UPDATED), eq("Product"), eq(salsa.getId()), anyMap());
    }

    @Test
    void preparationsThatUseEachOtherAreACycleAndNothingIsSaved() {
        Report report = service.importCatalog(List.of(),
                List.of(new PreparationRow(2, "Salsa A", BigDecimal.ONE, "kg", "salsa b", new BigDecimal("0.5"), "kg"),
                        new PreparationRow(3, "Salsa B", BigDecimal.ONE, "kg", "salsa a", new BigDecimal("0.5"), "kg")),
                List.of(), false, ALL, Set.of(), Set.of());

        assertFalse(report.saved());
        assertEquals(List.of(
                new RowError(Sheet.PREPARATIONS, 2, "ingredient", ErrorCode.IMPORT_PREPARATION_CYCLE, "Salsa A → Salsa B → Salsa A"),
                new RowError(Sheet.PREPARATIONS, 3, "ingredient", ErrorCode.IMPORT_PREPARATION_CYCLE, "Salsa B → Salsa A → Salsa B")),
                report.errors());
        assertNothingWritten();
    }

    @Test
    void aPreparationWithoutYieldOrWithAnUnknownIngredientSaysWhichField() {
        Report report = service.importCatalog(List.of(),
                List.of(new PreparationRow(2, "Fondo", null, "l", "hueso", new BigDecimal("1"), "kg")),
                List.of(), true, ALL, Set.of(), Set.of());

        assertEquals(List.of(new RowError(Sheet.PREPARATIONS, 2, "yieldQuantity", ErrorCode.IMPORT_REQUIRED_FIELD, null)),
                report.errors(), "the ingredient is not reported twice once the preparation is invalid");
    }

    @Test
    void unitThatDoesNotConvertToTheProductsUnitIsRejected() {
        Report report = service.importCatalog(List.of(),
                List.of(new RecipeRow(5, "Limonada", null, null, "limón", new BigDecimal("1"), "Litro")), false, ALL, Set.of());

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
                false, ALL, Set.of());

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
                false, ALL, Set.of());

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

    @Test
    void creatingACategoryNeedsItsPermissionAndSaysWhichOne() {
        Set<Permission> withoutCategories = EnumSet.allOf(Permission.class);
        withoutCategories.remove(Permission.CATEGORIES_CREATE);

        Report report = service.importCatalog(
                List.of(new ProductRow(2, "Papa", "kg", "Tubérculos", null, null),
                        new ProductRow(3, "Lechuga", "u", "verduras", null, null)), // ya existe: no hace falta el permiso
                List.of(), false, withoutCategories, Set.of());

        assertEquals(List.of(new RowError(Sheet.PRODUCTS, 2, "category", ErrorCode.IMPORT_CATEGORY_CREATE_FORBIDDEN, "Tubérculos")),
                report.errors());
        assertNothingWritten();
    }

    @Test
    void aNameThatMatchesTwoInTheCatalogIsAmbiguous() {
        Product otroLimon = new Product();
        otroLimon.setId(UUID.randomUUID());
        otroLimon.setName("limon");
        otroLimon.setDefaultUnitId(unidad.getId());
        products.put(otroLimon.getId(), otroLimon);
        MenuItem otraMilanesa = MenuItem.builder().id(UUID.randomUUID()).restaurantId(R).name("MILANESA").active(true).build();
        menuItems.put(otraMilanesa.getId(), otraMilanesa);

        Report report = service.importCatalog(
                List.of(new ProductRow(2, "Limón", "kg", null, null, null)),
                List.of(new RecipeRow(2, "Milanesa", null, null, "papa", new BigDecimal("1"), "kg"),
                        new RecipeRow(3, "Limonada", null, null, "limón", new BigDecimal("1"), "kg")),
                false, ALL, Set.of());

        assertEquals(List.of(
                new RowError(Sheet.PRODUCTS, 2, "name", ErrorCode.IMPORT_AMBIGUOUS_NAME, "Limón"),
                new RowError(Sheet.RECIPES, 2, "menuItem", ErrorCode.IMPORT_AMBIGUOUS_NAME, "Milanesa"),
                new RowError(Sheet.RECIPES, 2, "ingredient", ErrorCode.IMPORT_UNKNOWN_INGREDIENT, "papa"),
                new RowError(Sheet.RECIPES, 3, "ingredient", ErrorCode.IMPORT_AMBIGUOUS_NAME, "limón")),
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
