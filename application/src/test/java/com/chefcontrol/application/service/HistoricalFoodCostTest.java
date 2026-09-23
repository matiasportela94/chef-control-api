package com.chefcontrol.application.service;

import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.history.PriceHistoryEntry;
import com.chefcontrol.domain.history.RecipeVersion;
import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.product.Unit;
import com.chefcontrol.domain.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * ponytail: un solo escenario, pero el que justifica las tres tablas de historial.
 *
 * En marzo el plato llevaba 200 g de papa, la papa costaba $1.000/kg, rendía 100% y el plato
 * se vendía a $5.000. Hoy la receta lleva 500 g, la papa sale $3.000, rinde 80% y el plato vale
 * $20.000. Preguntar por marzo tiene que contestar con marzo: si alguna de las cuatro entradas
 * se toma del presente, el número cambia.
 */
@ExtendWith(MockitoExtension.class)
class HistoricalFoodCostTest {

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

    private final UUID restaurantId = UUID.randomUUID();
    private final UUID menuItemId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID kg = UUID.randomUUID();
    private final Instant marzo = Instant.parse("2027-03-15T12:00:00Z");

    @BeforeEach void setTenant() { TenantContext.set(restaurantId); }
    @AfterEach  void clearTenant() { TenantContext.clear(); }

    private FoodCostService service() {
        return new FoodCostService(saleRepository, saleItemRepository, stockMovementRepository,
                menuItemRepository, recipeRepository, productRepository, unitConversionService,
                priceHistoryRepository, recipeVersionRepository, unitRepository);
    }

    @Test
    void elCostoDeMarzo_usaLaRecetaElCostoElRendimientoYElPrecioDeMarzo() {
        MenuItem item = new MenuItem();
        item.setId(menuItemId);
        item.setRestaurantId(restaurantId);
        item.setName("Puré");
        item.setPrice(new BigDecimal("20000")); // el precio de HOY, no debe usarse
        when(menuItemRepository.findByIdAndRestaurantId(menuItemId, restaurantId))
                .thenReturn(Optional.of(item));

        Product papa = new Product();
        papa.setId(productId);
        papa.setName("Papa");
        papa.setDefaultUnitId(kg);
        papa.setYieldPercentage(new BigDecimal("80")); // el rendimiento de HOY
        when(productRepository.findByIdAndRestaurantId(productId, restaurantId))
                .thenReturn(Optional.of(papa));

        // La receta que regía en marzo: 0,2 kg (hoy lleva 0,5)
        when(recipeVersionRepository.findByMenuItemIdAt(menuItemId, marzo))
                .thenReturn(Optional.of(new RecipeVersion(UUID.randomUUID(), restaurantId, menuItemId,
                        1, marzo, null,
                        List.of(new RecipeVersion.Item(productId, new BigDecimal("0.2"), kg)))));

        // El rendimiento y el precio que regían en marzo
        when(priceHistoryRepository.findProductYieldAt(productId, marzo))
                .thenReturn(Optional.of(new PriceHistoryEntry(null, restaurantId, productId,
                        new BigDecimal("100"), marzo, null)));
        when(priceHistoryRepository.findMenuItemPriceAt(menuItemId, marzo))
                .thenReturn(Optional.of(new PriceHistoryEntry(null, restaurantId, menuItemId,
                        new BigDecimal("5000"), marzo, null)));

        // El costo de compra cortado en marzo
        when(stockMovementRepository.getWeightedAvgPurchaseCostAsOf(productId, restaurantId, marzo))
                .thenReturn(new BigDecimal("1000"));
        when(unitConversionService.convert(any(), eq(kg), eq(kg))).thenAnswer(inv -> inv.getArgument(0));
        Unit unit = new Unit();
        unit.setName("Kilogramo");
        when(unitRepository.findById(kg)).thenReturn(Optional.of(unit));

        var report = service().calculateRecipeCostAt(menuItemId, marzo);

        // 0,2 kg al 100% de rendimiento × $1.000 = $200. Con los datos de hoy daría
        // 0,5 / 0,80 × $3.000 = $1.875, y sobre un precio de $20.000.
        assertThat(report.costPerServing()).isEqualByComparingTo("200");
        assertThat(report.menuPrice()).isEqualByComparingTo("5000");
        assertThat(report.foodCostPercentage()).isEqualByComparingTo("4.00");
        assertThat(report.ingredients()).singleElement()
                .satisfies(i -> assertThat(i.yieldPercentage()).isEqualByComparingTo("100"));
    }
}
