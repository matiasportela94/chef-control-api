package com.chefcontrol.application.service;

import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.menu.Recipe;
import com.chefcontrol.domain.menu.RecipeItem;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.repository.*;
import com.chefcontrol.domain.sale.Sale;
import com.chefcontrol.domain.sale.SaleItem;
import com.chefcontrol.domain.stock.MovementType;
import com.chefcontrol.domain.stock.StockMovement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ponytail: el riesgo real del rendimiento no es la división —eso lo cubre ProductYieldTest—
 * sino que el stock se descuente de menos. Acá se verifica que SALE + WASTE suman el bruto
 * y que la merma estándar cuelga del sale_item (de eso depende que reverseSale la revierta).
 */
@ExtendWith(MockitoExtension.class)
class SaleServiceYieldTest {

    @Mock SaleRepository saleRepository;
    @Mock SaleItemRepository saleItemRepository;
    @Mock MenuItemRepository menuItemRepository;
    @Mock RecipeRepository recipeRepository;
    @Mock StockMovementRepository stockMovementRepository;
    @Mock StockBatchService stockBatchService;
    @Mock AlertEvaluationService alertEvaluationService;
    @Mock AuditService auditService;
    @Mock CurrentUserProvider currentUserProvider;
    @Mock ProductRepository productRepository;
    @Mock UnitConversionService unitConversionService;

    private final UUID restaurantId = UUID.randomUUID();
    private final UUID menuItemId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();
    private final UUID saleItemId = UUID.randomUUID();

    @BeforeEach void setTenant() { TenantContext.set(restaurantId); }
    @AfterEach  void clearTenant() { TenantContext.clear(); }

    private SaleService service() {
        return new SaleService(saleRepository, saleItemRepository, menuItemRepository, recipeRepository,
                stockMovementRepository, stockBatchService, alertEvaluationService, auditService,
                currentUserProvider, productRepository, unitConversionService);
    }

    private List<StockMovement> movementsForSaleOf(String yieldPercentage) {
        return movementsForSaleOf(yieldPercentage, "200");
    }

    /** Vende 1 plato cuya receta pide {@code recipeQuantity} de un producto con ese rendimiento. */
    private List<StockMovement> movementsForSaleOf(String yieldPercentage, String recipeQuantity) {
        MenuItem menuItem = new MenuItem();
        menuItem.setId(menuItemId);
        menuItem.setRestaurantId(restaurantId);
        menuItem.setName("Puré");
        menuItem.setPrice(new BigDecimal("5000"));
        menuItem.setActive(true);
        when(menuItemRepository.findByIdAndRestaurantId(menuItemId, restaurantId)).thenReturn(Optional.of(menuItem));

        Product product = new Product();
        product.setId(productId);
        product.setDefaultUnitId(unitId);
        product.setYieldPercentage(new BigDecimal(yieldPercentage));
        when(productRepository.findByIdAndRestaurantId(productId, restaurantId)).thenReturn(Optional.of(product));

        Recipe recipe = Recipe.builder()
                .menuItemId(menuItemId).restaurantId(restaurantId).servings(1)
                .items(List.of(RecipeItem.builder()
                        .productId(productId).productName("Papa")
                        .quantity(new BigDecimal(recipeQuantity)).unitId(unitId).build()))
                .build();
        when(recipeRepository.findByMenuItemIdAndRestaurantId(menuItemId, restaurantId))
                .thenReturn(Optional.of(recipe));

        when(unitConversionService.convert(any(), eq(unitId), eq(unitId))).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.getWeightedAvgPurchaseCost(productId, restaurantId)).thenReturn(BigDecimal.TEN);
        when(stockMovementRepository.getCurrentStock(productId, restaurantId)).thenReturn(new BigDecimal("1000"));
        when(saleRepository.save(any())).thenAnswer(inv -> {
            Sale s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(saleItemRepository.save(any())).thenAnswer(inv ->
                SaleItem.builder().id(saleItemId).menuItemId(menuItemId).quantity(1)
                        .unitPrice(new BigDecimal("5000")).build());
        when(stockMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().recordSale(new SaleService.CreateSaleCommand(null, null,
                List.of(new SaleService.SaleItemCommand(menuItemId, 1))));

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void rendimiento90_parteElConsumoEnSaleNetoYMermaEstandar() {
        List<StockMovement> movements = movementsForSaleOf("90");

        StockMovement sale = movements.stream().filter(m -> m.getType() == MovementType.SALE).findFirst().orElseThrow();
        StockMovement waste = movements.stream().filter(m -> m.getType() == MovementType.WASTE).findFirst().orElseThrow();

        assertThat(sale.getQuantity()).isEqualByComparingTo("200.000");
        assertThat(waste.getQuantity()).isEqualByComparingTo("22.222");
        // 200 / 0,90 = 222,222 — el stock se descuenta por el bruto, no por lo que entra a la olla.
        assertThat(sale.getQuantity().add(waste.getQuantity())).isEqualByComparingTo("222.222");

        // De esto depende que reverseSale() revierta también la merma: busca por sale_item.
        assertThat(waste.getReferenceType()).isEqualTo("sale_item");
        assertThat(waste.getReferenceId()).isEqualTo(saleItemId);
    }

    @Test
    void rendimiento250_descuentaSoloElBruto_sinMerma() {
        List<StockMovement> movements = movementsForSaleOf("250");

        assertThat(movements).noneMatch(m -> m.getType() == MovementType.WASTE);
        StockMovement sale = movements.stream().filter(m -> m.getType() == MovementType.SALE).findFirst().orElseThrow();
        assertThat(sale.getQuantity()).isEqualByComparingTo("80.000");
    }

    /**
     * El bug que esto cierra: con redondeo a 2 decimales, 5 g de sal de un producto medido en kg
     * (0,005) se guardaban como 0,01 — el doble — y 4 g como 0. Nada que ver con el rendimiento:
     * pasaba en toda receta con cantidades chicas sobre una unidad grande.
     */
    @Test
    void cantidadChica_noSePierdeEnElRedondeo() {
        List<StockMovement> movements = movementsForSaleOf("100", "0.005");

        StockMovement sale = movements.stream().filter(m -> m.getType() == MovementType.SALE).findFirst().orElseThrow();
        assertThat(sale.getQuantity()).isEqualByComparingTo("0.005");
    }

    @Test
    void rendimiento100_unicoMovimiento_comoAntes() {
        List<StockMovement> movements = movementsForSaleOf("100");

        assertThat(movements).noneMatch(m -> m.getType() == MovementType.WASTE);
        StockMovement sale = movements.stream().filter(m -> m.getType() == MovementType.SALE).findFirst().orElseThrow();
        assertThat(sale.getQuantity()).isEqualByComparingTo("200.000");
    }
}
