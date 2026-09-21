package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.finance.FoodCostMetric;
import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.menu.Recipe;
import com.chefcontrol.domain.repository.MenuItemRepository;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.RecipeRepository;
import com.chefcontrol.domain.repository.SaleItemRepository;
import com.chefcontrol.domain.repository.SaleRepository;
import com.chefcontrol.domain.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FoodCostService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final MenuItemRepository menuItemRepository;
    private final RecipeRepository recipeRepository;
    private final ProductRepository productRepository;
    private final UnitConversionService unitConversionService;

    public FoodCostReport calculate(Instant from, Instant to) {
        var restaurantId = TenantContext.require();

        BigDecimal revenue = saleRepository
                .sumTotalAmountByRestaurantIdAndSoldAtBetween(restaurantId, from, to);

        BigDecimal theoreticalCost = stockMovementRepository
                .sumSalesCost(restaurantId, from, to);

        FoodCostMetric metric = new FoodCostMetric(revenue, theoreticalCost);
        return new FoodCostReport(from, to, revenue, theoreticalCost, metric.percentage());
    }

    /**
     * Breaks down a recipe's theoretical cost ingredient by ingredient, using each product's
     * weighted average purchase cost — the same cost basis the global food cost report uses,
     * so the numbers reconcile with each other.
     */
    public RecipeCostReport calculateRecipeCost(UUID menuItemId) {
        UUID restaurantId = TenantContext.require();

        MenuItem menuItem = menuItemRepository.findByIdAndRestaurantId(menuItemId, restaurantId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.MENU_ITEM_NOT_FOUND, "Menu item not found"));

        Recipe recipe = recipeRepository.findByMenuItemIdAndRestaurantId(menuItemId, restaurantId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.RECIPE_NOT_FOUND,
                        "Recipe not found for menu item: " + menuItemId));

        List<RecipeIngredientCost> ingredients = recipe.getItems().stream()
                .map(item -> {
                    Product product = productRepository
                            .findByIdAndRestaurantId(item.getProductId(), restaurantId)
                            .orElse(null);
                    UUID defaultUnitId = product != null ? product.getDefaultUnitId() : item.getUnitId();
                    BigDecimal qtyInDefaultUnit = unitConversionService.convert(
                            item.getQuantity(), item.getUnitId(), defaultUnitId);
                    // El costo se cobra sobre lo que hay que comprar, no sobre lo que entra a la olla:
                    // 200 g de papa pelada al 90% se pagan como 222,22 g. El costo se calcula en la
                    // unidad del producto (que es la del precio) pero se informa en la de la receta,
                    // para que las dos cantidades de la pantalla se puedan comparar entre sí.
                    BigDecimal grossInDefaultUnit = product != null
                            ? product.grossQuantityFor(qtyInDefaultUnit)
                            : qtyInDefaultUnit;
                    // Se aplica el rendimiento directo sobre la cantidad de la receta en vez de
                    // convertir el bruto de vuelta: la ida y vuelta entre unidades pierde precisión.
                    BigDecimal grossInRecipeUnit = product != null
                            ? product.grossQuantityFor(item.getQuantity())
                            : item.getQuantity();
                    BigDecimal unitCost = stockMovementRepository
                            .getWeightedAvgPurchaseCost(item.getProductId(), restaurantId);
                    BigDecimal totalCost = grossInDefaultUnit.multiply(unitCost).setScale(4, RoundingMode.HALF_UP);
                    return new RecipeIngredientCost(
                            item.getProductId(), item.getProductName(),
                            item.getQuantity(), item.getUnitId(), item.getUnitName(),
                            grossInRecipeUnit, product != null ? product.getYieldPercentage() : null,
                            unitCost, totalCost);
                })
                .toList();

        BigDecimal totalCost = ingredients.stream()
                .map(RecipeIngredientCost::totalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal costPerServing = totalCost.divide(BigDecimal.valueOf(recipe.getServings()), 4, RoundingMode.HALF_UP);

        BigDecimal menuPrice = menuItem.getPrice() != null ? menuItem.getPrice() : BigDecimal.ZERO;
        BigDecimal foodCostPercentage = new FoodCostMetric(menuPrice, costPerServing).percentage();

        return new RecipeCostReport(menuItem.getId(), menuItem.getName(), recipe.getServings(), menuPrice,
                ingredients, totalCost, costPerServing, foodCostPercentage);
    }

    public MenuItemFoodCostReport calculateMenuItemFoodCost(UUID menuItemId, Instant from, Instant to) {
        UUID restaurantId = TenantContext.require();

        MenuItem menuItem = menuItemRepository.findByIdAndRestaurantId(menuItemId, restaurantId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.MENU_ITEM_NOT_FOUND, "Menu item not found"));

        BigDecimal revenue = saleItemRepository.sumRevenueByMenuItemAndPeriod(menuItemId, restaurantId, from, to);
        BigDecimal realizedCost = stockMovementRepository.sumSalesCostByMenuItemAndPeriod(menuItemId, restaurantId, from, to);
        int quantitySold = saleItemRepository.sumQuantitySoldByMenuItemAndPeriod(menuItemId, restaurantId, from, to);

        BigDecimal foodCostPercentage = new FoodCostMetric(revenue, realizedCost).percentage();

        return new MenuItemFoodCostReport(menuItem.getId(), menuItem.getName(),
                from, to, quantitySold, revenue, realizedCost, foodCostPercentage);
    }

    public record FoodCostReport(
            Instant from,
            Instant to,
            BigDecimal revenue,
            BigDecimal theoreticalCost,
            BigDecimal foodCostPercentage
    ) {}

    /**
     * {@code quantity} es lo que dice la receta (lo que entra a la olla) y {@code grossQuantity}
     * lo que hay que comprar para tenerlo, ya dividido por el rendimiento. Las dos van en la
     * <b>misma unidad</b> —la de la receta— porque la pantalla las muestra una al lado de la otra
     * y comparar 200 g contra 0,222 kg no le sirve a nadie. {@code totalCost} se cobra sobre el bruto.
     */
    public record RecipeIngredientCost(
            UUID productId,
            String productName,
            BigDecimal quantity,
            UUID unitId,
            String unitName,
            BigDecimal grossQuantity,
            BigDecimal yieldPercentage,
            BigDecimal unitCost,
            BigDecimal totalCost
    ) {}

    public record MenuItemFoodCostReport(
            UUID menuItemId,
            String menuItemName,
            Instant from,
            Instant to,
            int quantitySold,
            BigDecimal revenue,
            BigDecimal realizedCost,
            BigDecimal foodCostPercentage
    ) {}

    public record RecipeCostReport(
            UUID menuItemId,
            String menuItemName,
            int servings,
            BigDecimal menuPrice,
            List<RecipeIngredientCost> ingredients,
            BigDecimal totalCost,
            BigDecimal costPerServing,
            BigDecimal foodCostPercentage
    ) {}
}
