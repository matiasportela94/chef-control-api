package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.menu.Recipe;
import com.chefcontrol.domain.menu.RecipeItem;
import com.chefcontrol.domain.repository.*;
import com.chefcontrol.domain.restaurant.Restaurant;
import com.chefcontrol.domain.restaurant.TaxCondition;
import com.chefcontrol.domain.sale.Sale;
import com.chefcontrol.domain.sale.SaleItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * El IVA de las ventas de punta a punta en los servicios: la alícuota que se congela al vender, el reporte y que
 * el food cost (realizado y teórico) vaya sobre el ingreso sin IVA.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SalesIvaServiceTest {

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
    @Mock RestaurantRepository restaurantRepository;
    @Mock PriceHistoryRepository priceHistoryRepository;
    @Mock RecipeVersionRepository recipeVersionRepository;
    @Mock UnitRepository unitRepository;

    private final UUID restaurantId = UUID.randomUUID();
    private final UUID menuItemId = UUID.randomUUID();
    private final Restaurant restaurant = new Restaurant();

    @BeforeEach
    void setUp() {
        TenantContext.set(restaurantId);
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        MenuItem menuItem = new MenuItem();
        menuItem.setId(menuItemId);
        menuItem.setName("Milanesa");
        menuItem.setPrice(new BigDecimal("12100"));
        menuItem.setActive(true);
        when(menuItemRepository.findByIdAndRestaurantId(menuItemId, restaurantId)).thenReturn(Optional.of(menuItem));
        when(saleRepository.save(any())).thenAnswer(inv -> {
            Sale s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(saleItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void aResponsableInscriptoFreezesItsRateAndTheNetOfEachLine() {
        restaurant.setTaxCondition(TaxCondition.RESPONSABLE_INSCRIPTO);

        Sale sale = sellTwo();

        assertThat(sale.getIvaPercentage()).isEqualByComparingTo("21");
        assertThat(sale.getTotalAmount()).isEqualByComparingTo("24200");
        assertThat(sale.getNetTotal()).isEqualByComparingTo("20000");
        ArgumentCaptor<SaleItem> line = ArgumentCaptor.forClass(SaleItem.class);
        verify(saleItemRepository).save(line.capture());
        assertThat(line.getValue().getNetAmount()).isEqualByComparingTo("20000");
        assertThat(line.getValue().getSaleId()).isEqualTo(sale.getId()); // la línea queda colgada de su venta
    }

    @Test
    void monotributoAndNoConditionFreezeZero() {
        assertThat(sellTwo().getIvaPercentage()).isEqualByComparingTo("0"); // sin condición
        restaurant.setTaxCondition(TaxCondition.MONOTRIBUTO);
        Sale sale = sellTwo();

        assertThat(sale.getIvaPercentage()).isEqualByComparingTo("0");
        assertThat(sale.getNetTotal()).isEqualByComparingTo(sale.getTotalAmount());
    }

    @Test
    void theReportGroupsByRateAndKeepsLegacySalesApart() {
        Sale ri21 = Sale.builder().id(UUID.randomUUID()).totalAmount(new BigDecimal("2420.00"))
                .ivaPercentage(new BigDecimal("21")).netTotal(new BigDecimal("2000.00")).build();
        Sale ri21b = Sale.builder().id(UUID.randomUUID()).totalAmount(new BigDecimal("121.00"))
                .ivaPercentage(new BigDecimal("21.00")).netTotal(new BigDecimal("100.00")).build();
        Sale ri105 = Sale.builder().id(UUID.randomUUID()).totalAmount(new BigDecimal("1105.00"))
                .ivaPercentage(new BigDecimal("10.5")).netTotal(new BigDecimal("1000.00")).build();
        Sale legacy = Sale.builder().id(UUID.randomUUID()).totalAmount(new BigDecimal("500.00")).build();
        Instant from = Instant.parse("2026-11-01T03:00:00Z"), to = Instant.parse("2026-12-01T02:59:59Z");
        when(saleRepository.findActiveByRestaurantIdAndSoldAtBetween(restaurantId, from, to))
                .thenReturn(List.of(ri21, legacy, ri105, ri21b));

        SaleService.SalesIvaReport report = saleService().getSalesIvaReport(from, to);

        assertThat(report.netTotal()).isEqualByComparingTo("3100");
        assertThat(report.ivaTotal()).isEqualByComparingTo("546");
        assertThat(report.total()).isEqualByComparingTo("3646");
        assertThat(report.byRate()).hasSize(2); // 21 y 21.00 son la misma alícuota
        assertThat(report.byRate().get(0).ivaPercentage()).isEqualByComparingTo("10.5");
        assertThat(report.byRate().get(1).netAmount()).isEqualByComparingTo("2100");
        assertThat(report.byRate().get(1).ivaAmount()).isEqualByComparingTo("441");
        assertThat(report.sales()).hasSize(4);
        assertThat(report.salesWithoutIvaData()).isEqualTo(1);
        assertThat(report.totalWithoutIvaData()).isEqualByComparingTo("500");
    }

    @Test
    void realizedFoodCostIsOverTheNetRevenue() {
        Instant from = Instant.parse("2026-11-01T00:00:00Z"), to = Instant.parse("2026-11-30T00:00:00Z");
        when(saleRepository.sumTotalAmountByRestaurantIdAndSoldAtBetween(restaurantId, from, to)).thenReturn(new BigDecimal("12100"));
        when(saleRepository.sumNetRevenueByRestaurantIdAndSoldAtBetween(restaurantId, from, to)).thenReturn(new BigDecimal("10000"));
        when(stockMovementRepository.sumSalesCost(restaurantId, from, to)).thenReturn(new BigDecimal("3000"));

        FoodCostService.FoodCostReport report = foodCostService().calculate(from, to);

        assertThat(report.revenue()).isEqualByComparingTo("12100"); // lo cobrado sigue a la vista
        assertThat(report.netRevenue()).isEqualByComparingTo("10000");
        assertThat(report.foodCostPercentage()).isEqualByComparingTo("30.00"); // no 24,79
    }

    @Test
    void theoreticalFoodCostOfAResponsableInscriptoIsOverThePriceWithoutIva() {
        restaurant.setTaxCondition(TaxCondition.RESPONSABLE_INSCRIPTO);
        UUID productId = UUID.randomUUID(), unitId = UUID.randomUUID();
        when(recipeRepository.findByMenuItemIdAndRestaurantId(menuItemId, restaurantId)).thenReturn(Optional.of(
                Recipe.builder().menuItemId(menuItemId).servings(1).items(List.of(RecipeItem.builder()
                        .productId(productId).quantity(BigDecimal.ONE).unitId(unitId).build())).build()));
        when(unitConversionService.convert(any(), eq(unitId), eq(unitId))).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.findLastPurchaseCostPerUnit(productId, restaurantId)).thenReturn(new BigDecimal("3000"));

        FoodCostService.RecipeCostReport ri = foodCostService().calculateRecipeCost(menuItemId);
        assertThat(ri.menuPrice()).isEqualByComparingTo("12100");
        assertThat(ri.netPrice()).isEqualByComparingTo("10000");
        assertThat(ri.foodCostPercentage()).isEqualByComparingTo("30.00");

        restaurant.setTaxCondition(TaxCondition.MONOTRIBUTO);
        FoodCostService.RecipeCostReport mono = foodCostService().calculateRecipeCost(menuItemId);
        assertThat(mono.netPrice()).isEqualByComparingTo("12100");
        assertThat(mono.foodCostPercentage()).isEqualByComparingTo("24.79");
    }

    @Test
    void aSalesRateOutsideTheArgentineOnesIsRejected() {
        RestaurantRegistrationService registration = new RestaurantRegistrationService(null, null, restaurantRepository,
                null, null, null, null, auditService);
        restaurant.setAccountId(UUID.randomUUID());
        when(restaurantRepository.findByIdAndIsActiveTrue(restaurantId)).thenReturn(Optional.of(restaurant));

        assertThatThrownBy(() -> registration.updateRestaurant(restaurantId, new RestaurantRegistrationService.CreateRestaurantCommand(
                "Local", null, TaxCondition.RESPONSABLE_INSCRIPTO, new BigDecimal("19"))))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_IVA_PERCENTAGE);
        verify(restaurantRepository, never()).save(any());
    }

    private Sale sellTwo() {
        return saleService().recordSale(new SaleService.CreateSaleCommand(null, null,
                List.of(new SaleService.SaleItemCommand(menuItemId, 2))));
    }

    private SaleService saleService() {
        return new SaleService(saleRepository, saleItemRepository, menuItemRepository, recipeRepository,
                stockMovementRepository, stockBatchService, alertEvaluationService, auditService,
                currentUserProvider, productRepository, unitConversionService, restaurantRepository);
    }

    private FoodCostService foodCostService() {
        return new FoodCostService(saleRepository, saleItemRepository, stockMovementRepository, menuItemRepository,
                recipeRepository, productRepository, unitConversionService, priceHistoryRepository,
                recipeVersionRepository, unitRepository, restaurantRepository);
    }
}
