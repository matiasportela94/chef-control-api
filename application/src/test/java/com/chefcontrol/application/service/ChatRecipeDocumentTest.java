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
    private final ChatCatalog catalog = new ChatCatalog(List.of(papa, fileto), List.of(noquis), List.of(), Set.of(fileto.getId()));
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
                List.of(), List.of(), List.of(), List.of(new DishServings("ñoquis", new BigDecimal("4")))), List.of("Papa", "Huevo"));

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

    private static Product product(String name) {
        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setName(name);
        return p;
    }
}
