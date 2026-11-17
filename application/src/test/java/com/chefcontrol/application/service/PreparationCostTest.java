package com.chefcontrol.application.service;

import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.menu.Recipe;
import com.chefcontrol.domain.menu.RecipeItem;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * El teórico de una preparación es lo que cuesta hacerla hoy, recursivo: mayonesa base (0,8 l de aceite a $2000 + 4
 * huevos a $200, rinde 1 kg) = $2400/kg; mil islas (0,6 kg de mayonesa + 0,4 kg de ketchup a $3000, rinde 1 kg) = $2640.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PreparationCostTest {

    @Mock SaleRepository saleRepository;
    @Mock SaleItemRepository saleItemRepository;
    @Mock StockMovementRepository stockMovementRepository;
    @Mock MenuItemRepository menuItemRepository;
    @Mock RecipeRepository recipeRepository;
    @Mock ProductRepository productRepository;
    @Mock UnitConversionService unitConversionService;
    @Mock PriceHistoryRepository priceHistoryRepository;
    @Mock RecipeVersionRepository recipeVersionRepository;
    @Mock UnitRepository unitRepository;
    @Mock RestaurantRepository restaurantRepository;

    private final UUID r = UUID.randomUUID();
    private final UUID kg = UUID.randomUUID(), l = UUID.randomUUID(), u = UUID.randomUUID();
    private final UUID aceite = UUID.randomUUID(), huevo = UUID.randomUUID(), ketchup = UUID.randomUUID();
    private final UUID mayo = UUID.randomUUID(), milIslas = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.set(r);
        when(unitConversionService.convert(any(), any(), any())).thenAnswer(i -> i.getArgument(0)); // misma unidad
        product(aceite, l, "2000");
        product(huevo, u, "200");
        product(ketchup, kg, "3000");
        product(mayo, kg, null);
        product(milIslas, kg, null);
        preparation(mayo, item(aceite, "0.8", l), item(huevo, "4", u));
        preparation(milIslas, item(mayo, "0.6", kg), item(ketchup, "0.4", kg));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private FoodCostService service() {
        return new FoodCostService(saleRepository, saleItemRepository, stockMovementRepository, menuItemRepository,
                recipeRepository, productRepository, unitConversionService, priceHistoryRepository,
                recipeVersionRepository, unitRepository, restaurantRepository);
    }

    private void product(UUID id, UUID unit, String lastPurchase) {
        Product p = new Product();
        p.setId(id);
        p.setDefaultUnitId(unit);
        p.setYieldPercentage(new BigDecimal("100"));
        when(productRepository.findByIdAndRestaurantId(id, r)).thenReturn(Optional.of(p));
        when(stockMovementRepository.findLastPurchaseCostPerUnit(id, r))
                .thenReturn(lastPurchase == null ? null : new BigDecimal(lastPurchase));
    }

    private RecipeItem item(UUID productId, String quantity, UUID unit) {
        return RecipeItem.builder().productId(productId).quantity(new BigDecimal(quantity)).unitId(unit).build();
    }

    private void preparation(UUID productId, RecipeItem... items) {
        when(recipeRepository.findByProductIdAndRestaurantId(productId, r)).thenReturn(Optional.of(Recipe.builder()
                .productId(productId).restaurantId(r).yieldQuantity(BigDecimal.ONE).items(List.of(items)).build()));
    }

    @Test
    void thePreparationCostsWhatMakingItCostsToday() {
        assertEquals(0, new BigDecimal("2400").compareTo(service().theoreticalUnitCost(mayo, r, null)));
        assertEquals(0, new BigDecimal("2640").compareTo(service().theoreticalUnitCost(milIslas, r, null)));
    }

    @Test
    void evenIfItWasAlsoBoughtTheRecipeWins() {
        // B4: comprar una preparación se permite, pero el teórico es su receta
        when(stockMovementRepository.findLastPurchaseCostPerUnit(mayo, r)).thenReturn(new BigDecimal("9999"));
        assertEquals(0, new BigDecimal("2400").compareTo(service().theoreticalUnitCost(mayo, r, null)));
    }

    @Test
    void theBreakdownSaysWhichIngredientIsAPreparation() {
        var report = service().calculatePreparationCost(milIslas, null);

        assertEquals(0, new BigDecimal("2640").compareTo(report.unitCost()));
        assertTrue(report.ingredients().stream().filter(i -> i.productId().equals(mayo)).findFirst().orElseThrow().preparation());
        assertFalse(report.ingredients().stream().filter(i -> i.productId().equals(ketchup)).findFirst().orElseThrow().preparation());
    }
}
