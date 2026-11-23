package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AIService.CatalogSpreadsheetInterpretation;
import com.chefcontrol.application.port.AIService.DishServings;
import com.chefcontrol.application.service.CatalogImportService.PreparationRow;
import com.chefcontrol.application.service.CatalogImportService.ProductRow;
import com.chefcontrol.application.service.CatalogImportService.RecipeRow;
import com.chefcontrol.application.service.ChatService.ChatCatalog;
import com.chefcontrol.application.service.ChatService.ChatStatus;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.menu.Recipe;
import com.chefcontrol.domain.plan.Feature;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.repository.RecipeRepository;
import com.chefcontrol.domain.user.Permission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** F19, tanda 9: la foto de una o varias recetas → la vista previa del import; y las porciones las divide el back. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatRecipeDocumentTest {

    @Mock RecipeRepository recipeRepository;
    @Mock AccountService accountService;

    private static final UUID R = UUID.randomUUID();
    private static final Set<Permission> ALL = EnumSet.allOf(Permission.class);

    private final Product papa = product("Papa");
    private final Product fileto = product("Salsa fileto");
    private final MenuItem noquis = MenuItem.builder().id(UUID.randomUUID()).name("Ñoquis de papa").price(BigDecimal.TEN).build();
    private final ChatCatalog catalog = new ChatCatalog(List.of(papa, fileto), List.of(noquis),
            List.of(unit("Kilogramo", "kg"), unit("Gramo", "g"), unit("Unidad", "u"), unit("Litro", "l")), Set.of(fileto.getId()));
    private ChatRecipeService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(R);
        service = new ChatRecipeService(null, recipeRepository, accountService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void theServingsAreDividedByTheBackAndSaidInAWarning() {
        var r = CatalogSpreadsheetService.applyMatches(new CatalogSpreadsheetInterpretation(List.of(), List.of(),
                List.of(new RecipeRow(1, "Ñoquis", null, null, "Papa", new BigDecimal("1"), "kg"),
                        new RecipeRow(2, "Ñoquis", null, null, "Huevo", new BigDecimal("2"), "u"),
                        new RecipeRow(3, "Flan", null, null, "Huevo", new BigDecimal("6"), "u")),
                List.of(), List.of(), List.of(), List.of(new DishServings("ñoquis", new BigDecimal("4")))), List.of("Papa", "Huevo"),
                catalog.units());

        assertEquals(0, new BigDecimal("0.25").compareTo(r.recipes().get(0).quantity()));
        assertEquals(0, new BigDecimal("0.5").compareTo(r.recipes().get(1).quantity()));
        assertEquals(0, new BigDecimal("6").compareTo(r.recipes().get(2).quantity()));       // el flan no dijo porciones
        assertTrue(r.warnings().contains("«ñoquis» rinde 4 porciones: puse las cantidades de una."), r.warnings().toString());
        assertTrue(r.dishServings().isEmpty());                                                 // ya aplicadas
    }

    @Test
    void aPhotoWithTwoRecipesGoesToThePreviewWithTheExistingOnesMarkedToReplace() {
        var result = service.previewDocumentRecipes(new CatalogSpreadsheetInterpretation(List.of(), List.of(
                        new PreparationRow(3, "salsa fileto", new BigDecimal("1.5"), "l", "Tomate", new BigDecimal("2"), "kg")),
                List.of(new RecipeRow(1, "ÑOQUIS DE PAPA", null, null, "papas", new BigDecimal("1"), "kg"),
                        new RecipeRow(2, "ÑOQUIS DE PAPA", null, null, "Nuez moscada", new BigDecimal("2"), "g")),
                List.of(new ProductRow(2, "nuez moscada", "kg", null, null, null)), List.of(), List.of(),
                List.of(new DishServings("ÑOQUIS DE PAPA", new BigDecimal("2")))), catalog, ALL);

        assertEquals(ChatStatus.RECIPE_PREVIEW, result.status(), result.message());
        var preview = result.recipe();
        assertNull(preview.name());                                                             // son dos
        assertEquals(List.of(noquis.getId()), preview.replaceRecipeMenuItemIds());
        assertEquals(List.of(fileto.getId()), preview.replacePreparationProductIds());
        var rows = preview.catalogImport().recipes();
        assertEquals("Ñoquis de papa", rows.getFirst().menuItem());                             // como en el catálogo
        assertEquals("Papa", rows.getFirst().ingredient());                                     // plural, por el back
        assertEquals(0, new BigDecimal("0.5").compareTo(rows.getFirst().quantity()));           // 1 kg para 2 porciones
        assertEquals("Salsa fileto", preview.catalogImport().preparations().getFirst().preparation());
        assertEquals("Nuez moscada", preview.catalogImport().proposedProducts().getFirst().name());
        assertTrue(result.message().contains("2 recetas"), result.message());
    }

    @Test
    void aDishThatIsAPreparationInTheCatalogGoesAsAPreparationWithItsYield() {
        Recipe current = new Recipe();
        current.setYieldQuantity(new BigDecimal("1.5"));
        when(recipeRepository.findByProductIdAndRestaurantId(fileto.getId(), R)).thenReturn(Optional.of(current));

        var preview = service.previewDocumentRecipes(new CatalogSpreadsheetInterpretation(List.of(), List.of(),
                List.of(new RecipeRow(1, "Salsa fileto", null, null, "Tomate", new BigDecimal("2"), "kg")),
                List.of(), List.of(), List.of()), catalog, ALL).recipe();

        assertTrue(preview.catalogImport().recipes().isEmpty());
        var prep = preview.catalogImport().preparations().getFirst();
        assertEquals("Salsa fileto", prep.preparation());
        assertEquals(0, new BigDecimal("1.5").compareTo(prep.yieldQuantity()));
        assertEquals("Salsa fileto", preview.name());
    }

    @Test
    void withoutThePermissionsOrThePlanNothingIsPreviewed() {
        var recipe = new CatalogSpreadsheetInterpretation(List.of(), List.of(),
                List.of(new RecipeRow(1, "Ñoquis de papa", null, null, "Papa", BigDecimal.ONE, "kg")),
                List.of(), List.of(), List.of());

        var denied = service.previewDocumentRecipes(recipe, catalog, EnumSet.of(Permission.AI_USE, Permission.PRODUCTS_CREATE));
        assertEquals(ChatStatus.NOT_REGISTERED, denied.status());
        assertEquals(ErrorCode.CHAT_PERMISSION_MISSING, denied.skipped().getFirst().errorCode());

        doThrow(AppException.forbidden(ErrorCode.PLAN_FEATURE_REQUIRED, "plan"))
                .when(accountService).requireFeature(Feature.RECIPES_AND_FOOD_COST);
        var noPlan = service.previewDocumentRecipes(recipe, catalog, ALL);
        assertEquals(ChatStatus.NOT_REGISTERED, noPlan.status());
        assertEquals(ErrorCode.PLAN_FEATURE_REQUIRED, noPlan.skipped().getFirst().errorCode());
    }

    @Test
    void aUnitOutsideTheListLeavesTheIngredientOutAndSaysTheRecipeIsIncomplete() {
        var result = service.previewDocumentRecipes(new CatalogSpreadsheetInterpretation(List.of(), List.of(),
                List.of(new RecipeRow(1, "Ñoquis de papa", null, null, "Papa", BigDecimal.ONE, "kg"),
                        new RecipeRow(2, "Ñoquis de papa", null, null, "Harina", new BigDecimal("2"), "tazas"),
                        new RecipeRow(3, "Ñoquis de papa", null, null, "Huevo", new BigDecimal("2"), "unidades")),
                List.of(new ProductRow(2, "Harina", "kg", null, null, null)), List.of(), List.of(),
                List.of(new DishServings("Ñoquis de papa", new BigDecimal("4")))), catalog, ALL);

        var rows = result.recipe().catalogImport().recipes();
        assertEquals(List.of("Papa", "Huevo"), rows.stream().map(RecipeRow::ingredient).toList());
        assertEquals("u", rows.get(1).unit());                                       // "unidades" es la de la lista
        assertTrue(result.recipe().catalogImport().proposedProducts().isEmpty());   // la harina no la usa nadie: no se propone
        // El aviso dice lo impreso, no lo dividido por las 4 porciones.
        assertTrue(result.recipe().catalogImport().warnings().contains(
                "«Harina» de «Ñoquis de papa»: 2 tazas no es una unidad que maneje; la dejé afuera, agregala a mano."),
                result.recipe().catalogImport().warnings().toString());
    }

    @Test
    void theChatRecipeKeepsAPriceOnlyIfTheMessageSaysIt() {
        var r = new CatalogSpreadsheetInterpretation(List.of(), List.of(),
                List.of(new RecipeRow(1, "Milanesa", BigDecimal.ZERO, null, "Tomate", new BigDecimal("300"), "g")),
                List.of(), List.of(), List.of());
        assertNull(ChatRecipeService.keepWhatTheMessageSays(r, "subile el tomate a 300 gramos").recipes().getFirst().price());
        var priced = new CatalogSpreadsheetInterpretation(List.of(), List.of(),
                List.of(new RecipeRow(1, "Milanesa", new BigDecimal("9500"), null, "Tomate", new BigDecimal("300"), "g")),
                List.of(), List.of(), List.of());
        assertNull(ChatRecipeService.keepWhatTheMessageSays(priced, "la milanesa lleva 300 g de tomate").recipes().getFirst().price());
        assertEquals(0, new BigDecimal("9500").compareTo(ChatRecipeService.keepWhatTheMessageSays(priced,
                "la milanesa va a $9.500 y lleva 300 g de tomate").recipes().getFirst().price()));
        var mil = new CatalogSpreadsheetInterpretation(List.of(), List.of(),
                List.of(new RecipeRow(1, "Milanesa", new BigDecimal("12000"), null, "Tomate", new BigDecimal("300"), "g")),
                List.of(), List.of(), List.of());
        assertEquals(0, new BigDecimal("12000").compareTo(ChatRecipeService.keepWhatTheMessageSays(mil,
                "la milanesa a 12 mil, con 300 g de tomate").recipes().getFirst().price()));
    }

    @Test
    void theChatRecipeKeepsASectionOnlyIfTheMessageNamesIt() {
        var r = new CatalogSpreadsheetInterpretation(List.of(), List.of(),
                List.of(new RecipeRow(1, "Napolitana", null, "Principales", "Tomate", new BigDecimal("150"), "g")),
                List.of(), List.of(), List.of());
        assertNull(ChatRecipeService.keepWhatTheMessageSays(r, "la napolitana lleva 150 gramos de tomate")
                .recipes().getFirst().section());
        assertEquals("Principales", ChatRecipeService.keepWhatTheMessageSays(r,
                "la napolitana va en principales y lleva 150 gramos de tomate").recipes().getFirst().section());
    }

    @Test
    void aProposedProductTakesTheRecipeUnitWhenItsOwnDoesNotConvert() {
        var kg = catalog.units().get(0);
        var g = catalog.units().get(1);
        g.setBaseUnitId(kg.getId());   // g y kg convierten; u, con nada de eso
        var r = CatalogSpreadsheetService.applyMatches(new CatalogSpreadsheetInterpretation(List.of(),
                List.of(new PreparationRow(1, "Fileto", new BigDecimal("1.5"), "l", "Cebolla", BigDecimal.ONE, "u"),
                        new PreparationRow(2, "Fileto", null, null, "Ajo", new BigDecimal("20"), "g")),
                List.of(), List.of(new ProductRow(1, "Cebolla", "kg", null, null, null),
                        new ProductRow(2, "Ajo", "kg", null, null, null)), List.of(), List.of()), List.of(), catalog.units());

        assertEquals("u", r.proposedProducts().get(0).unit());       // "1 cebolla": se propone en u
        assertEquals("kg", r.proposedProducts().get(1).unit());      // 20 g de ajo: kg convierte, se queda
    }

    private static com.chefcontrol.domain.product.Unit unit(String name, String abbreviation) {
        var u = new com.chefcontrol.domain.product.Unit();
        u.setId(UUID.randomUUID());
        u.setName(name);
        u.setAbbreviation(abbreviation);
        return u;
    }

    private static Product product(String name) {
        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setName(name);
        return p;
    }
}
