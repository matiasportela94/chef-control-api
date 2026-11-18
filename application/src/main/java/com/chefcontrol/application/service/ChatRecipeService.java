package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AIService;
import com.chefcontrol.application.port.AIService.CatalogSpreadsheetInterpretation;
import com.chefcontrol.application.port.AIService.CurrentRecipe;
import com.chefcontrol.application.port.AIService.InterpretedItem;
import com.chefcontrol.application.port.AIService.RecipeLine;
import com.chefcontrol.application.port.AIService.RecipeMessageRequest;
import com.chefcontrol.application.service.CatalogImportService.PreparationRow;
import com.chefcontrol.application.service.ChatService.ChatCatalog;
import com.chefcontrol.application.service.ChatService.Skipped;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.menu.Recipe;
import com.chefcontrol.domain.plan.Feature;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.product.Unit;
import com.chefcontrol.domain.repository.RecipeRepository;
import com.chefcontrol.domain.user.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Las recetas del chat (F19, tanda 4). Un mensaje que crea o cambia la receta de un plato o de una preparación nunca se
 * guarda en el acto: termina en la vista previa del import, porque una receta es un dato maestro y un error se paga en
 * el food cost de todo lo que se venda. Son dos llamadas a la IA, solo en estos mensajes: la del chat dice de qué receta
 * se trata y esta le pasa al modelo la receta actual, para que devuelva la completa con el cambio aplicado.
 */
@Service
@RequiredArgsConstructor
public class ChatRecipeService {

    private static final Set<Permission> DISH_RECIPE = EnumSet.of(
            Permission.PRODUCTS_CREATE, Permission.MENU_CREATE, Permission.MENU_UPDATE);
    private static final Set<Permission> PREPARATION_RECIPE = EnumSet.of(
            Permission.PRODUCTS_CREATE, Permission.PRODUCTS_UPDATE);

    private final AIService aiService;
    private final RecipeRepository recipeRepository;
    private final AccountService accountService;

    /**
     * Lo que el front lleva a /catalog-import (dryRun y después la confirmación), tal cual.
     *
     * @param replaceRecipeMenuItemIds     el plato, si ya existía: pedir el cambio por chat ya es decir que se pisa
     * @param replacePreparationProductIds la preparación, ídem
     */
    public record RecipePreview(String name, CatalogSpreadsheetInterpretation catalogImport,
                                List<UUID> replaceRecipeMenuItemIds, List<UUID> replacePreparationProductIds) {}

    /** Una receta tal como la detectó la primera llamada: el ítem trae el plato o la preparación y su nombre. */
    record RecipeRequest(boolean preparation, InterpretedItem item) {}

    /**
     * Una receta por mensaje: las demás van a {@code skipped}. Sin permiso o sin el plan, tampoco se llama al modelo.
     * Los permisos son los mismos que va a pedir /catalog-import con esas filas.
     *
     * @return null si no hay receta para mostrar (y el motivo en {@code skipped})
     */
    RecipePreview previewChatRecipe(String message, List<RecipeRequest> requests, ChatCatalog catalog,
                                    Set<Permission> permissions, List<Skipped> skipped) {
        if (requests.isEmpty()) return null;
        requests.stream().skip(1).forEach(r -> skipped.add(new Skipped(null, r.item().name(),
                ErrorCode.CHAT_OPERATION_NOT_SUPPORTED,
                "Una receta por mensaje: mandá «" + r.item().name() + "» en otro mensaje.")));

        RecipeRequest request = requests.getFirst();
        InterpretedItem item = request.item();
        Target target = target(request, catalog);
        boolean preparation = target.preparation();
        MenuItem dish = target.dish();
        Product product = target.product();
        String name = dish != null ? dish.getName() : product != null ? product.getName() : capitalized(item.name());
        if (name == null) {
            skipped.add(new Skipped(null, null, ErrorCode.AI_INTERPRETATION_FAILED,
                    "No entendí de qué plato o preparación es la receta."));
            return null;
        }
        if (!permissions.containsAll(preparation ? PREPARATION_RECIPE : DISH_RECIPE)) {
            skipped.add(new Skipped(null, name, ErrorCode.CHAT_PERMISSION_MISSING, "No tenés permiso para cargar recetas."));
            return null;
        }
        try {
            accountService.requireFeature(Feature.RECIPES_AND_FOOD_COST);
        } catch (AppException e) {
            skipped.add(new Skipped(null, name, e.getErrorCode(), "Las recetas no están incluidas en tu plan."));
            return null;
        }

        UUID restaurantId = TenantContext.require();
        Recipe recipe = dish != null ? recipeRepository.findByMenuItemIdAndRestaurantId(dish.getId(), restaurantId).orElse(null)
                : product != null ? recipeRepository.findByProductIdAndRestaurantId(product.getId(), restaurantId).orElse(null)
                : null;
        CurrentRecipe current = recipe == null || recipe.getItems().isEmpty() ? null : current(recipe, product, catalog);

        CatalogSpreadsheetInterpretation answer;
        try {
            answer = aiService.interpretRecipeMessage(new RecipeMessageRequest(message, name, preparation, current,
                    catalog.chatProducts(), catalog.unitOptions()));
        } catch (AppException e) {
            if (e.getErrorCode() != ErrorCode.AI_INTERPRETATION_FAILED) throw e;
            skipped.add(new Skipped(null, name, e.getErrorCode(), "No pude armar la receta de «" + name + "». Probá de nuevo."));
            return null;
        }
        answer = CatalogSpreadsheetService.applyMatches(answer, catalog.products().stream().map(Product::getName).toList());
        if (answer.recipes().isEmpty() && answer.preparations().isEmpty()) {
            skipped.add(new Skipped(null, name, ErrorCode.AI_INTERPRETATION_FAILED,
                    "No entendí los ingredientes de «" + name + "»."));
            return null;
        }
        if (preparation && current != null) answer = keepYield(answer, current);
        return new RecipePreview(name, answer,
                dish != null ? List.of(dish.getId()) : List.of(),
                product != null ? List.of(product.getId()) : List.of());
    }

    private record Target(boolean preparation, MenuItem dish, Product product) {}

    /**
     * De qué receta se trata. Deciden lo que la IA reconoció del catálogo, no el tipo que eligió: en la prueba del
     * 2026-11-18, "a la salsa de tomate agregale 2 huevos" volvió como receta de plato con el id de la preparación, y
     * por el tipo se habría armado un plato nuevo "Salsa de tomate". Sin ids, el nombre; el tipo solo desempata.
     */
    private static Target target(RecipeRequest request, ChatCatalog catalog) {
        InterpretedItem item = request.item();
        MenuItem dish = item.menuItemId() == null ? null : catalog.menuItem(item.menuItemId());
        Product product = item.productId() == null ? null : catalog.product(item.productId());
        if (dish == null && product == null && item.name() != null) {
            dish = catalog.menuItemNamed(item.name());
            product = catalog.productNamed(item.name());
            // Un insumo comprado que se llama como el plato no es la receta: solo una preparación, o si lo pidió así.
            if (product != null && !request.preparation() && !catalog.preparations().contains(product.getId())) {
                product = null;
            }
        }
        if (dish != null && product != null) {
            return request.preparation() ? new Target(true, null, product) : new Target(false, dish, null);
        }
        if (dish != null) return new Target(false, dish, null);
        if (product != null) return new Target(true, null, product);
        return new Target(request.preparation(), null, null);
    }

    /** Un plato va por porción (como lo guarda el import); una preparación, por tanda, con lo que rinde. */
    private static CurrentRecipe current(Recipe recipe, Product preparation, ChatCatalog catalog) {
        BigDecimal servings = BigDecimal.valueOf(Math.max(recipe.getServings(), 1));
        List<RecipeLine> lines = new ArrayList<>();
        recipe.getItems().forEach(i -> {
            Product p = catalog.product(i.getProductId());
            Unit u = catalog.unit(i.getUnitId());
            BigDecimal quantity = preparation != null ? i.getQuantity()
                    : i.getQuantity().divide(servings, 6, RoundingMode.HALF_UP).stripTrailingZeros();
            lines.add(new RecipeLine(p != null ? p.getName() : i.getProductName(), quantity,
                    u == null ? null : u.getAbbreviation()));
        });
        return preparation == null ? new CurrentRecipe(null, null, lines)
                : new CurrentRecipe(recipe.getYieldQuantity(), ChatService.unitLabel(preparation), lines);
    }

    /** Si el modelo no repitió el rendimiento de una preparación que ya lo tenía, va el de hoy: el import lo pide. */
    private static CatalogSpreadsheetInterpretation keepYield(CatalogSpreadsheetInterpretation r, CurrentRecipe current) {
        if (r.preparations().getFirst().yieldQuantity() != null) return r;
        List<PreparationRow> rows = new ArrayList<>(r.preparations());
        PreparationRow first = rows.getFirst();
        rows.set(0, new PreparationRow(first.row(), first.preparation(), current.yieldQuantity(), current.yieldUnit(),
                first.ingredient(), first.quantity(), first.unit()));
        return new CatalogSpreadsheetInterpretation(r.products(), rows, r.recipes(), r.proposedProducts(),
                r.ingredientMatches(), r.warnings());
    }

    private static String capitalized(String name) {
        if (name == null || name.isBlank()) return null;
        String n = name.trim().replaceAll("\\s+", " ");
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}
