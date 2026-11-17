package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.history.RecipeVersion;
import com.chefcontrol.domain.menu.Recipe;
import com.chefcontrol.domain.menu.RecipeItem;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.product.Unit;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.RecipeRepository;
import com.chefcontrol.domain.repository.RecipeVersionRepository;
import com.chefcontrol.domain.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * La receta de una preparación: qué lleva una tanda y cuánto rinde. Un insumo es preparación porque tiene esta receta
 * (Mati, 2026-11-17: sin flag aparte). Vive en las mismas tablas que la receta de un plato, con el insumo como dueño.
 */
@Service
@RequiredArgsConstructor
public class PreparationService {

    private static final BigDecimal MAX_QUANTITY = new BigDecimal("1000000000"); // NUMERIC(12,3)

    private final ProductRepository productRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeVersionRepository recipeVersionRepository;
    private final UnitRepository unitRepository;
    private final UnitConversionService unitConversionService;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;

    public Optional<Recipe> getPreparationRecipe(UUID productId) {
        UUID restaurantId = TenantContext.require();
        requireProduct(productId, restaurantId);
        return recipeRepository.findByProductIdAndRestaurantId(productId, restaurantId);
    }

    /** Los insumos del restaurante que son preparaciones (para el listado de insumos). */
    public Set<UUID> preparationIds() {
        return recipeRepository.findPreparationProductIds(TenantContext.require());
    }

    @Transactional
    public Recipe setPreparationRecipe(UUID productId, SetPreparationRecipeCommand cmd) {
        UUID restaurantId = TenantContext.require();
        Product preparation = requireProduct(productId, restaurantId);
        if (cmd.yieldQuantity() == null || cmd.yieldQuantity().signum() <= 0
                || cmd.yieldQuantity().compareTo(MAX_QUANTITY) >= 0) {
            throw AppException.badRequest(ErrorCode.VALIDATION_ERROR, "The yield must be greater than zero");
        }
        if (cmd.items() == null || cmd.items().isEmpty()) {
            throw AppException.badRequest(ErrorCode.VALIDATION_ERROR, "A preparation needs at least one ingredient");
        }

        List<RecipeItem> items = cmd.items().stream().map(itemCmd -> {
            if (itemCmd.productId().equals(productId)) {
                throw AppException.badRequest(ErrorCode.PREPARATION_CYCLE,
                        "A preparation can't use itself: " + preparation.getName());
            }
            Product ingredient = requireProduct(itemCmd.productId(), restaurantId);
            Unit unit = unitRepository.findById(itemCmd.unitId())
                    .orElseThrow(() -> AppException.notFound(ErrorCode.UNIT_NOT_FOUND, "Unit not found: " + itemCmd.unitId()));
            if (itemCmd.quantity() == null || itemCmd.quantity().signum() <= 0
                    || itemCmd.quantity().compareTo(MAX_QUANTITY) >= 0) {
                throw AppException.badRequest(ErrorCode.VALIDATION_ERROR, "Invalid quantity for " + ingredient.getName());
            }
            // Producir descuenta el ingrediente convirtiendo a su unidad: que falle al guardar, no al producir.
            unitConversionService.convert(itemCmd.quantity(), unit.getId(), ingredient.getDefaultUnitId());
            RecipeItem item = new RecipeItem();
            item.setProductId(ingredient.getId());
            item.setQuantity(itemCmd.quantity());
            item.setUnitId(unit.getId());
            return item;
        }).toList();

        requireNoCycle(preparation, items.stream().map(RecipeItem::getProductId).toList(), restaurantId);

        Recipe recipe = recipeRepository.findByProductIdAndRestaurantId(productId, restaurantId).orElseGet(() -> {
            Recipe r = new Recipe();
            r.setProductId(productId);
            r.setRestaurantId(restaurantId);
            return r;
        });
        recipe.setServings(1);
        recipe.setYieldQuantity(cmd.yieldQuantity());
        recipe.replaceItems(items);
        Recipe saved = recipeRepository.save(recipe);

        // Misma regla que la receta del plato: una versión solo si cambió (el ?at= y la evolución leen de acá).
        RecipeVersion candidate = RecipeVersion.forPreparation(restaurantId, productId, cmd.yieldQuantity(),
                currentUserProvider.currentUserId(), saved.getItems().stream()
                        .map(i -> new RecipeVersion.Item(i.getProductId(), i.getQuantity(), i.getUnitId())).toList());
        boolean unchanged = recipeVersionRepository.findLatestByProductId(productId)
                .map(candidate::sameContentAs).orElse(false);
        if (!unchanged) recipeVersionRepository.save(candidate);

        Map<String, Object> payload = new HashMap<>();
        payload.put("productId", productId);
        payload.put("name", preparation.getName());
        payload.put("yieldQuantity", cmd.yieldQuantity());
        payload.put("ingredients", items.size());
        auditService.log(AuditAction.PRODUCT_RECIPE_UPDATED, "Product", productId, payload);
        return saved;
    }

    /** Deja de ser preparación; su stock y sus lotes quedan. La versión vacía marca desde cuándo no tiene receta. */
    @Transactional
    public void deletePreparationRecipe(UUID productId) {
        UUID restaurantId = TenantContext.require();
        requireProduct(productId, restaurantId);
        recipeRepository.findByProductIdAndRestaurantId(productId, restaurantId).ifPresent(recipe -> {
            recipeRepository.delete(recipe);
            recipeVersionRepository.save(RecipeVersion.forPreparation(restaurantId, productId, null,
                    currentUserProvider.currentUserId(), List.of()));
            auditService.log(AuditAction.PRODUCT_RECIPE_DELETED, "Product", productId, Map.of("productId", productId));
        });
    }

    /** Con la receta nueva, ¿alguna de sus preparaciones termina llevando a esta? */
    private void requireNoCycle(Product preparation, List<UUID> newIngredients, UUID restaurantId) {
        Map<UUID, Collection<UUID>> cache = new HashMap<>();
        cache.put(preparation.getId(), newIngredients);
        PreparationCycles.findCycle(preparation.getId(), id -> cache.computeIfAbsent(id, k ->
                        recipeRepository.findByProductIdAndRestaurantId(k, restaurantId)
                                .map(r -> r.getItems().stream().map(RecipeItem::getProductId).toList())
                                .orElse(List.of())))
                .ifPresent(cycle -> {
                    String names = cycle.stream().map(id -> productRepository.findByIdAndRestaurantId(id, restaurantId)
                            .map(Product::getName).orElse(id.toString())).collect(Collectors.joining(" → "));
                    throw AppException.badRequest(ErrorCode.PREPARATION_CYCLE, "The preparation uses itself: " + names);
                });
    }

    private Product requireProduct(UUID productId, UUID restaurantId) {
        return productRepository.findByIdAndRestaurantId(productId, restaurantId)
                .filter(Product::isActive)
                .orElseThrow(() -> AppException.notFound(ErrorCode.PRODUCT_NOT_FOUND, "Product not found: " + productId));
    }

    public record SetPreparationRecipeCommand(BigDecimal yieldQuantity, List<PreparationItemCommand> items) {}

    public record PreparationItemCommand(UUID productId, BigDecimal quantity, UUID unitId) {}
}
