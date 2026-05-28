package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.menu.Recipe;
import com.chefcontrol.domain.menu.RecipeItem;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.product.Unit;
import com.chefcontrol.domain.repository.MenuItemRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final RecipeRepository recipeRepository;
    private final ProductRepository productRepository;
    private final UnitRepository unitRepository;
    private final AuditService auditService;

    public Page<MenuItem> listMenuItems(PageRequest pageRequest) {
        return menuItemRepository.findByRestaurantIdAndActiveTrue(TenantContext.require(), pageRequest);
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
                .category(cmd.category())
                .active(true)
                .build();
        item = menuItemRepository.save(item);
        auditService.log(AuditAction.RECIPE_CREATED, "MenuItem", item.getId(),
                Map.of("name", cmd.name()));
        return item;
    }

    @Transactional
    public MenuItem updateMenuItem(UUID id, UpdateMenuItemCommand cmd) {
        MenuItem item = getMenuItem(id);
        if (cmd.name() != null) item.setName(cmd.name());
        if (cmd.description() != null) item.setDescription(cmd.description());
        if (cmd.price() != null) item.setPrice(cmd.price());
        if (cmd.category() != null) item.setCategory(cmd.category());
        return menuItemRepository.save(item);
    }

    @Transactional
    public void deactivateMenuItem(UUID id) {
        MenuItem item = getMenuItem(id);
        item.deactivate();
        menuItemRepository.save(item);
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
        auditService.log(isNew ? AuditAction.RECIPE_CREATED : AuditAction.RECIPE_UPDATED,
                "Recipe", saved.getId(), Map.of("menuItemId", menuItemId));
        return saved;
    }

    @Transactional
    public void deleteRecipe(UUID menuItemId) {
        UUID restaurantId = TenantContext.require();
        getMenuItem(menuItemId); // validates ownership
        recipeRepository.findByMenuItemIdAndRestaurantId(menuItemId, restaurantId)
                .ifPresent(recipe -> {
                    recipeRepository.delete(recipe);
                    auditService.log(AuditAction.RECIPE_DELETED, "Recipe", recipe.getId(),
                            Map.of("menuItemId", menuItemId));
                });
    }

    // ── Commands ─────────────────────────────────────────────────────────────

    public record CreateMenuItemCommand(String name, String description, BigDecimal price, String category) {}

    public record UpdateMenuItemCommand(String name, String description, BigDecimal price, String category) {}

    public record SetRecipeCommand(int servings, List<RecipeItemCommand> items) {}

    public record RecipeItemCommand(UUID productId, UUID unitId, BigDecimal quantity) {}
}
