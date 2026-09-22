package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.finance.FoodCostMetric;
import com.chefcontrol.domain.history.PriceHistoryEntry;
import com.chefcontrol.domain.history.RecipeVersion;
import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.menu.Recipe;
import com.chefcontrol.domain.repository.MenuItemRepository;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.PriceHistoryRepository;
import com.chefcontrol.domain.repository.RecipeVersionRepository;
import com.chefcontrol.domain.repository.RecipeRepository;
import com.chefcontrol.domain.repository.SaleItemRepository;
import com.chefcontrol.domain.repository.SaleRepository;
import com.chefcontrol.domain.repository.StockMovementRepository;
import com.chefcontrol.domain.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.TreeSet;
import java.util.Set;
import java.util.LinkedHashSet;
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
    private final PriceHistoryRepository priceHistoryRepository;
    private final RecipeVersionRepository recipeVersionRepository;
    private final UnitRepository unitRepository;

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

    /**
     * El food cost teórico que regía en {@code at}: no "el de hoy mirado desde atrás".
     *
     * <p>Las cuatro entradas salen de su propia historia, y las cuatro hacían falta — con tres de
     * cuatro el número sale mal de una forma difícil de notar:
     * <ul>
     *   <li>la <b>composición de la receta</b>, de {@code recipe_versions} (V23);</li>
     *   <li>el <b>costo de compra</b> de cada insumo, del ledger cortado en esa fecha;</li>
     *   <li>el <b>rendimiento</b> de cada insumo, de {@code product_yield_history} (V22);</li>
     *   <li>el <b>precio de venta</b> del plato, de {@code menu_item_price_history} (V22).</li>
     * </ul>
     *
     * <p><b>Límite conocido:</b> las series arrancan el 2026-09-21 con un punto sembrado en el
     * {@code created_at} de cada entidad. Para fechas anteriores a eso, el valor que devuelve es
     * el que había el día del backfill — no el que realmente regía. El frontend lo avisa.
     */
    public RecipeCostReport calculateRecipeCostAt(UUID menuItemId, Instant at) {
        UUID restaurantId = TenantContext.require();

        MenuItem menuItem = menuItemRepository.findByIdAndRestaurantId(menuItemId, restaurantId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.MENU_ITEM_NOT_FOUND, "Menu item not found"));

        RecipeVersion version = recipeVersionRepository.findByMenuItemIdAt(menuItemId, at)
                .orElseThrow(() -> AppException.notFound(ErrorCode.RECIPE_NOT_FOUND,
                        "El plato no tenía receta en esa fecha"));

        List<RecipeIngredientCost> ingredients = version.items().stream()
                .map(item -> {
                    Product product = productRepository
                            .findByIdAndRestaurantId(item.productId(), restaurantId)
                            .orElse(null);
                    // Acá el fallback al valor de hoy sí conviene: V22 sembró una fila por
                    // producto, así que no debería faltar ninguna — y si falta, costear con el
                    // rendimiento actual es mejor que no devolver el costo del plato.
                    BigDecimal yieldAt = priceHistoryRepository.findProductYieldAt(item.productId(), at)
                            .map(PriceHistoryEntry::value)
                            .orElse(product != null ? product.getYieldPercentage() : null);
                    BigDecimal unitCost = stockMovementRepository
                            .getWeightedAvgPurchaseCostAsOf(item.productId(), restaurantId, at);
                    return ingredientCost(product, item.productId(), item.quantity(), item.unitId(),
                            yieldAt, unitCost, restaurantId);
                })
                .toList();

        BigDecimal menuPrice = priceHistoryRepository.findMenuItemPriceAt(menuItemId, at)
                .map(PriceHistoryEntry::value)
                .orElse(menuItem.getPrice() != null ? menuItem.getPrice() : BigDecimal.ZERO);

        // Si en esa fecha ningún insumo tenía compras todavía, el costo no es cero: es
        // desconocido. Devolver cero pone un "food cost 0%" en el gráfico, que se lee como que
        // el plato salía gratis en vez de como que no hay con qué calcularlo.
        boolean noCostData = !ingredients.isEmpty() && ingredients.stream()
                .allMatch(i -> i.unitCost() == null || i.unitCost().compareTo(BigDecimal.ZERO) == 0);
        if (noCostData) {
            return new RecipeCostReport(menuItem.getId(), menuItem.getName(), version.servings(),
                    menuPrice, ingredients, null, null, null);
        }

        BigDecimal totalCost = ingredients.stream()
                .map(RecipeIngredientCost::totalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal costPerServing = version.servings() == 0 ? BigDecimal.ZERO
                : totalCost.divide(BigDecimal.valueOf(version.servings()), 4, RoundingMode.HALF_UP);

        return new RecipeCostReport(menuItem.getId(), menuItem.getName(), version.servings(), menuPrice,
                ingredients, totalCost, costPerServing,
                new FoodCostMetric(menuPrice, costPerServing).percentage());
    }

    /**
     * Desde cuándo las series de historial son verdad.
     *
     * <p>V22 y V23 sembraron un punto inicial por entidad con {@code valid_from = created_at} y el
     * valor que tenía el día del backfill. Para fechas anteriores a esta, lo que devuelven las
     * consultas históricas es el presente proyectado hacia atrás, no lo que realmente regía.
     * La pantalla lo avisa en vez de dejar que alguien lea un número inventado.
     *
     * <p>Es una constante y no una consulta a propósito: es la fecha en que se desplegaron las
     * migraciones, no un dato que viva en la base.
     */
    public Instant seriesReliableFrom() {
        return HISTORY_STARTS_AT;
    }

    /** Fecha de despliegue de V22/V23 — ver {@link #seriesReliableFrom()}. */
    private static final Instant HISTORY_STARTS_AT = Instant.parse("2026-09-21T00:00:00Z");

    /**
     * La evolución del plato en un período: qué precio tenía, cuánto costaba hacerlo y qué food
     * cost daba, en cada momento en que algo de eso cambió.
     *
     * <p>Los puntos son <b>los momentos en que algo cambió</b>: el precio de venta, la receta, o
     * el rendimiento de alguno de sus insumos. Los tres mueven el food cost, así que sampleando
     * solo los cambios de precio el gráfico mentiría por omisión — sacarle un ingrediente al plato
     * cambia el costo y no aparecería en ningún lado.
     *
     * <p>No es un muestreo por día ni por semana: entre dos cambios la línea es recta salvo por
     * las compras nuevas, y recalcular la receta entera una vez por día para dibujar eso no vale
     * lo que cuesta. Si hace falta esa resolución, se agrega después.
     *
     * <p>Los extremos del período siempre entran, para que la línea arranque y termine donde el
     * usuario pidió.
     */
    public List<PriceEvolutionPoint> calculatePriceEvolution(UUID menuItemId, Instant from, Instant to) {
        UUID restaurantId = TenantContext.require();
        menuItemRepository.findByIdAndRestaurantId(menuItemId, restaurantId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.MENU_ITEM_NOT_FOUND, "Menu item not found"));

        List<RecipeVersion> versionsInRange = recipeVersionRepository
                .findByMenuItemIdBetween(menuItemId, from, to);

        Set<Instant> moments = new TreeSet<>();
        moments.add(from);
        moments.add(to);
        addIfInRange(moments, priceHistoryRepository.findMenuItemPriceHistory(menuItemId).stream()
                .map(PriceHistoryEntry::validFrom), from, to);
        addIfInRange(moments, versionsInRange.stream().map(RecipeVersion::validFrom), from, to);

        // Los insumos que participaron en alguna versión del período: si a alguno le cambió el
        // rendimiento, el costo del plato se movió sin que nadie tocara la receta ni el precio.
        ingredientIdsOf(menuItemId, versionsInRange, to).forEach(productId ->
                addIfInRange(moments, priceHistoryRepository.findProductYieldHistory(productId).stream()
                        .map(PriceHistoryEntry::validFrom), from, to));

        return moments.stream().map(when -> pointAt(menuItemId, when)).toList();
    }

    private static void addIfInRange(Set<Instant> moments, Stream<Instant> candidates,
                                     Instant from, Instant to) {
        candidates.filter(when -> !when.isBefore(from) && !when.isAfter(to)).forEach(moments::add);
    }

    /** Los productos de todas las versiones del período, más los de la vigente al final. */
    private Set<UUID> ingredientIdsOf(UUID menuItemId, List<RecipeVersion> versionsInRange, Instant to) {
        Set<UUID> ids = versionsInRange.stream()
                .flatMap(v -> v.items().stream())
                .map(RecipeVersion.Item::productId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        recipeVersionRepository.findByMenuItemIdAt(menuItemId, to)
                .ifPresent(v -> v.items().forEach(i -> ids.add(i.productId())));
        return ids;
    }

    private PriceEvolutionPoint pointAt(UUID menuItemId, Instant when) {
        BigDecimal price = priceHistoryRepository.findMenuItemPriceAt(menuItemId, when)
                .map(PriceHistoryEntry::value)
                .orElse(null);
        try {
            RecipeCostReport report = calculateRecipeCostAt(menuItemId, when);
            return new PriceEvolutionPoint(when, report.menuPrice(),
                    report.costPerServing(), report.foodCostPercentage());
        } catch (AppException e) {
            // El plato no tenía receta en ese momento: el precio sigue siendo un dato válido
            // para graficar, el costo no. Devolver null es más honesto que devolver cero.
            return new PriceEvolutionPoint(when, price, null, null);
        }
    }

    /**
     * Cómo se movió el costo de un insumo: qué se pagó por unidad y qué rendimiento tenía, en
     * cada momento en que alguna de las dos cosas cambió.
     *
     * <p>Los puntos son las compras del período (cada una mueve el promedio ponderado) más los
     * cambios de rendimiento, más los extremos del rango.
     *
     * <p>A diferencia del plato, esta serie <b>no tiene el agujero del backfill del lado del
     * costo</b>: {@code stock_movements} es inmutable y está completo desde el día uno. El
     * rendimiento sí arranca con V22.
     */
    public List<ProductCostPoint> calculateProductCostEvolution(UUID productId, Instant from, Instant to) {
        UUID restaurantId = TenantContext.require();
        productRepository.findByIdAndRestaurantId(productId, restaurantId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.PRODUCT_NOT_FOUND, "Product not found"));

        Set<Instant> moments = new TreeSet<>();
        moments.add(from);
        moments.add(to);
        addIfInRange(moments,
                stockMovementRepository.findPurchaseDates(productId, restaurantId, from, to).stream(),
                from, to);
        addIfInRange(moments, priceHistoryRepository.findProductYieldHistory(productId).stream()
                .map(PriceHistoryEntry::validFrom), from, to);

        return moments.stream().map(when -> {
            BigDecimal purchaseCost = stockMovementRepository
                    .getWeightedAvgPurchaseCostAsOf(productId, restaurantId, when);
            // Sin fila de rendimiento en esa fecha el insumo todavía no existía. Devolver el
            // rendimiento de hoy sería proyectar el presente sobre un momento en el que no había
            // nada — el punto no tiene valores, y eso es lo que hay que decir.
            BigDecimal yieldPercentage = priceHistoryRepository.findProductYieldAt(productId, when)
                    .map(PriceHistoryEntry::value)
                    .orElse(null);

            // Lo que cuesta de verdad una unidad que llega al plato: si la papa rinde 90%, el kilo
            // útil sale 1/0,9 de lo que se pagó. Es el número con el que se costean las recetas.
            BigDecimal usableCost = purchaseCost;
            if (yieldPercentage != null && purchaseCost != null) {
                Product forYield = new Product();
                forYield.setYieldPercentage(yieldPercentage);
                usableCost = forYield.grossQuantityFor(purchaseCost);
            }
            boolean noPurchasesYet = purchaseCost == null || purchaseCost.compareTo(BigDecimal.ZERO) == 0;
            return new ProductCostPoint(when,
                    noPurchasesYet ? null : purchaseCost,
                    yieldPercentage,
                    noPurchasesYet ? null : usableCost);
        }).toList();
    }

    /**
     * El costo de un ingrediente, compartido entre el cálculo de hoy y el de una fecha pasada:
     * lo único que cambia entre los dos es de dónde salen el rendimiento y el costo unitario.
     */
    private RecipeIngredientCost ingredientCost(Product product, UUID productId, BigDecimal quantity,
                                                UUID unitId, BigDecimal yieldPercentage,
                                                BigDecimal unitCost, UUID restaurantId) {
        UUID defaultUnitId = product != null ? product.getDefaultUnitId() : unitId;
        BigDecimal qtyInDefaultUnit = unitConversionService.convert(quantity, unitId, defaultUnitId);

        BigDecimal grossInDefaultUnit = qtyInDefaultUnit;
        BigDecimal grossInRecipeUnit = quantity;
        if (yieldPercentage != null) {
            Product forYield = new Product();
            forYield.setYieldPercentage(yieldPercentage);
            grossInDefaultUnit = forYield.grossQuantityFor(qtyInDefaultUnit);
            grossInRecipeUnit = forYield.grossQuantityFor(quantity);
        }

        BigDecimal totalCost = grossInDefaultUnit.multiply(unitCost).setScale(4, RoundingMode.HALF_UP);
        return new RecipeIngredientCost(
                productId,
                product != null ? product.getName() : null,
                quantity, unitId, unitName(unitId),
                grossInRecipeUnit, yieldPercentage, unitCost, totalCost);
    }

    private String unitName(UUID unitId) {
        return unitRepository.findById(unitId).map(u -> u.getName()).orElse(null);
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

    /**
     * Un punto de la evolución de un insumo. {@code purchaseCost} es lo que se pagó por unidad
     * comprada y {@code usableCost} lo que cuesta la unidad que llega al plato, ya dividida por
     * el rendimiento. Null cuando todavía no había ninguna compra: devolver cero diría que el
     * insumo era gratis.
     */
    public record ProductCostPoint(
            Instant at,
            BigDecimal purchaseCost,
            BigDecimal yieldPercentage,
            BigDecimal usableCost
    ) {}

    /** Un punto de la evolución de un plato: qué valía, cuánto costaba y qué food cost daba. */
    public record PriceEvolutionPoint(
            Instant at,
            BigDecimal menuPrice,
            BigDecimal costPerServing,
            BigDecimal foodCostPercentage
    ) {}

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
