package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.history.PriceHistoryEntry;
import com.chefcontrol.domain.history.RecipeVersion;
import com.chefcontrol.domain.repository.PriceHistoryRepository;
import com.chefcontrol.domain.repository.RecipeVersionRepository;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.menu.Recipe;
import com.chefcontrol.domain.menu.RecipeItem;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.product.Unit;
import com.chefcontrol.domain.repository.CartaRepository;
import com.chefcontrol.domain.repository.MenuItemRepository;
import com.chefcontrol.domain.repository.MenuSectionRepository;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.RecipeRepository;
import com.chefcontrol.domain.repository.UnitRepository;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final CartaRepository cartaRepository;
    private final MenuSectionRepository menuSectionRepository;
    private final RecipeRepository recipeRepository;
    private final ProductRepository productRepository;
    private final UnitRepository unitRepository;
    private final AuditService auditService;
    private final PriceHistoryRepository priceHistoryRepository;
    private final RecipeVersionRepository recipeVersionRepository;
    private final CurrentUserProvider currentUserProvider;

    public Page<MenuItem> listMenuItems(boolean active, PageRequest pageRequest) {
        return menuItemRepository.findByRestaurantIdAndActive(TenantContext.require(), active, pageRequest);
    }

    public MenuItem getMenuItem(UUID id) {
        return menuItemRepository.findByIdAndRestaurantId(id, TenantContext.require())
                .orElseThrow(() -> AppException.notFound(ErrorCode.MENU_ITEM_NOT_FOUND, "Menu item not found"));
    }

    @Transactional
    public MenuItem createMenuItem(CreateMenuItemCommand cmd) {
        UUID restaurantId = TenantContext.require();
        MenuItem item = MenuItem.builder()
                .restaurantId(restaurantId)
                .name(cmd.name())
                .description(cmd.description())
                .price(cmd.price())
                .sectionId(resolveSectionId(cmd.sectionId(), restaurantId))
                .active(true)
                .build();
        item = menuItemRepository.save(item);
        recordPrice(item, null);
        auditService.log(AuditAction.MENU_ITEM_CREATED, "MenuItem", item.getId(),
                pricePayload(item));
        return item;
    }

    @Transactional
    public MenuItem updateMenuItem(UUID id, UpdateMenuItemCommand cmd) {
        MenuItem item = getMenuItem(id);
        BigDecimal previousPrice = item.getPrice();
        if (cmd.name() != null) item.setName(cmd.name());
        if (cmd.description() != null) item.setDescription(cmd.description());
        if (cmd.price() != null) item.setPrice(cmd.price());
        if (cmd.sectionId() != null) {
            item.setSectionId(resolveSectionId(cmd.sectionId(), item.getRestaurantId()));
        }
        item = menuItemRepository.save(item);
        recordPrice(item, previousPrice);
        auditService.log(AuditAction.MENU_ITEM_UPDATED, "MenuItem", item.getId(),
                pricePayload(item));
        return item;
    }

    /**
     * Agrega un punto a la serie de precios del plato, en la misma transacción que el cambio.
     *
     * <p>Solo escribe si el precio se movió: un save que no lo tocó no es un punto de la serie, y
     * si igual escribiera, leer "cuánto valía en marzo" sería recorrer filas repetidas.
     * Se compara con {@code compareTo} y no con {@code equals}: para {@code BigDecimal},
     * {@code 100} y {@code 100.00} no son iguales pero valen lo mismo.
     *
     * <p>A diferencia del audit log —async, otra transacción, se traga los errores— acá la
     * ausencia de fila significa una sola cosa: el precio no cambió.
     */
    private void recordPrice(MenuItem item, BigDecimal previousPrice) {
        BigDecimal current = item.getPrice();
        if (current == null) return; // un plato sin precio todavía no tiene serie que registrar
        if (previousPrice != null && previousPrice.compareTo(current) == 0) return;
        priceHistoryRepository.saveMenuItemPrice(PriceHistoryEntry.of(
                item.getRestaurantId(), item.getId(), current, currentUserProvider.currentUserId()));
    }

    /**
     * Payload de auditoría de un plato. Lleva el precio porque {@code menu_items.price} se pisa
     * al editar y el valor viejo no queda en ningún lado: leyendo la serie de entradas de este
     * plato en {@code audit_log} se reconstruye el historial de precios, con quién y cuándo.
     *
     * <p>Por eso el alta también lo graba: sin el punto inicial la serie arranca colgada.
     *
     * <p>{@code HashMap} y no {@code Map.of()} a propósito: el precio es opcional y
     * {@code Map.of()} tira NPE con un valor null.
     */
    private static Map<String, Object> pricePayload(MenuItem item) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", item.getName());
        payload.put("price", item.getPrice());
        return payload;
    }

    /** Dar de baja un plato lo saca de toda carta: una carta no ofrece algo que ya no existe. */
    @Transactional
    public void deactivateMenuItem(UUID id) {
        MenuItem item = getMenuItem(id);
        item.deactivate();
        menuItemRepository.save(item);
        cartaRepository.removeMenuItemFromAllCartas(item.getId());
        auditService.log(AuditAction.MENU_ITEM_DEACTIVATED, "MenuItem", item.getId(),
                Map.of("name", item.getName()));
    }

    @Transactional
    public void deactivateMenuItems(List<UUID> ids) {
        List<MenuItem> items = ids.stream().map(this::getMenuItem).toList(); // valida ownership de todos antes de tocar nada
        for (MenuItem item : items) {
            item.deactivate();
            menuItemRepository.save(item);
            cartaRepository.removeMenuItemFromAllCartas(item.getId());
        }
        auditService.log(AuditAction.MENU_ITEM_BULK_DEACTIVATED, "MenuItem", null,
                Map.of("count", items.size(), "menuItemIds", ids));
    }

    @Transactional
    public MenuItem activateMenuItem(UUID id) {
        MenuItem item = getMenuItem(id);
        item.activate();
        item = menuItemRepository.save(item);
        auditService.log(AuditAction.MENU_ITEM_REACTIVATED, "MenuItem", item.getId(),
                Map.of("name", item.getName()));
        return item;
    }

    /** Un plato solo puede apuntar a un paso del propio restaurante. */
    private UUID resolveSectionId(UUID sectionId, UUID restaurantId) {
        if (sectionId == null) return null;
        return menuSectionRepository.findByIdAndRestaurantId(sectionId, restaurantId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.MENU_SECTION_NOT_FOUND,
                        "Menu section not found: " + sectionId))
                .getId();
    }

    public Optional<Recipe> getRecipe(UUID menuItemId) {
        UUID restaurantId = TenantContext.require();
        getMenuItem(menuItemId); // validates ownership
        return recipeRepository.findByMenuItemIdAndRestaurantId(menuItemId, restaurantId);
    }

    @Transactional
    public Recipe setRecipe(UUID menuItemId, SetRecipeCommand cmd) {
        UUID restaurantId = TenantContext.require();
        MenuItem menuItem = getMenuItem(menuItemId);

        Recipe recipe = recipeRepository.findByMenuItemIdAndRestaurantId(menuItemId, restaurantId)
                .orElseGet(() -> {
                    Recipe r = new Recipe();
                    r.setMenuItemId(menuItem.getId());
                    r.setRestaurantId(restaurantId);
                    return r;
                });

        recipe.setServings(cmd.servings());

        List<RecipeItem> newItems = cmd.items().stream().map(itemCmd -> {
            Product product = productRepository.findByIdAndRestaurantId(itemCmd.productId(), restaurantId)
                    .filter(Product::isActive)
                    .orElseThrow(() -> AppException.notFound(ErrorCode.PRODUCT_NOT_FOUND,
                            "Product not found: " + itemCmd.productId()));
            Unit unit = unitRepository.findById(itemCmd.unitId())
                    .orElseThrow(() -> AppException.notFound(ErrorCode.UNIT_NOT_FOUND,
                            "Unit not found: " + itemCmd.unitId()));
            RecipeItem ri = new RecipeItem();
            ri.setRecipeId(recipe.getId());
            ri.setProductId(product.getId());
            ri.setQuantity(itemCmd.quantity());
            ri.setUnitId(unit.getId());
            return ri;
        }).toList();

        recipe.replaceItems(newItems);

        boolean isNew = recipe.getId() == null;
        Recipe saved = recipeRepository.save(recipe);
        recordRecipeVersion(saved, restaurantId);
        auditService.log(isNew ? AuditAction.RECIPE_CREATED : AuditAction.RECIPE_UPDATED,
                "Recipe", saved.getId(), Map.of("menuItemId", menuItemId));
        return saved;
    }

    /**
     * Guarda la composición de la receta como una versión nueva, en la misma transacción que el
     * save.
     *
     * <p>`replaceItems()` pisa los ítems anteriores, así que sin esto el food cost de un período
     * pasado se calcularía con la receta de hoy: sacarle la crema a un plato en abril cambiaría,
     * hacia atrás, lo que ese plato "costaba" en marzo.
     *
     * <p>Solo escribe si la receta cambió de verdad — mismo criterio que el precio y el
     * rendimiento. Reordenar los ingredientes no es un cambio (ver {@code sameContentAs}).
     */
    private void recordRecipeVersion(Recipe recipe, UUID restaurantId) {
        RecipeVersion candidate = RecipeVersion.of(restaurantId, recipe.getMenuItemId(),
                recipe.getServings(), currentUserProvider.currentUserId(),
                recipe.getItems().stream()
                        .map(i -> new RecipeVersion.Item(i.getProductId(), i.getQuantity(), i.getUnitId()))
                        .toList());

        boolean unchanged = recipeVersionRepository.findLatestByMenuItemId(recipe.getMenuItemId())
                .map(candidate::sameContentAs)
                .orElse(false);
        if (unchanged) return;

        recipeVersionRepository.save(candidate);
    }

    @Transactional
    public void deleteRecipe(UUID menuItemId) {
        UUID restaurantId = TenantContext.require();
        getMenuItem(menuItemId); // validates ownership
        recipeRepository.findByMenuItemIdAndRestaurantId(menuItemId, restaurantId)
                .ifPresent(recipe -> {
                    recipeRepository.delete(recipe);
                    // Una versión vacía marca "desde acá el plato no tiene receta". Sin esto,
                    // pedir el costo de una fecha posterior al borrado devolvería la última
                    // composición como si siguiera vigente.
                    recipeVersionRepository.save(RecipeVersion.of(restaurantId, menuItemId,
                            recipe.getServings(), currentUserProvider.currentUserId(), List.of()));
                    auditService.log(AuditAction.RECIPE_DELETED, "Recipe", recipe.getId(),
                            Map.of("menuItemId", menuItemId));
                });
    }

    // ── Commands ─────────────────────────────────────────────────────────────

    public record CreateMenuItemCommand(String name, String description, BigDecimal price, UUID sectionId) {}

    public record UpdateMenuItemCommand(String name, String description, BigDecimal price, UUID sectionId) {}

    public record SetRecipeCommand(int servings, List<RecipeItemCommand> items) {}

    public record RecipeItemCommand(UUID productId, UUID unitId, BigDecimal quantity) {}
}
