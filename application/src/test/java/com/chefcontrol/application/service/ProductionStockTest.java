package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.menu.Recipe;
import com.chefcontrol.domain.menu.RecipeItem;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.repository.*;
import com.chefcontrol.domain.stock.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** La producción automática por lo que falta, y la reversión de una producción con sus hijas. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductionStockTest {

    @Mock ProductRepository productRepository;
    @Mock RecipeRepository recipeRepository;
    @Mock ProductionRepository productionRepository;
    @Mock StockMovementRepository stockMovementRepository;
    @Mock StockBatchRepository stockBatchRepository;
    @Mock StockBatchService stockBatchService;
    @Mock UnitConversionService unitConversionService;
    @Mock AlertEvaluationService alertEvaluationService;
    @Mock FoodCostService foodCostService;
    @Mock AuditService auditService;
    @Mock CurrentUserProvider currentUserProvider;
    @Mock UnitRepository unitRepository;

    private final UUID r = UUID.randomUUID(), kg = UUID.randomUUID(), l = UUID.randomUUID();
    private final UUID mayo = UUID.randomUUID(), aceite = UUID.randomUUID(), saleItemId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.set(r);
        when(unitConversionService.convert(any(), any(), any())).thenAnswer(i -> i.getArgument(0));
        // Como el adaptador: devuelve el movimiento con id (el dominio no tiene setters).
        when(stockMovementRepository.save(any())).thenAnswer(i -> {
            StockMovement m = i.getArgument(0);
            return StockMovement.builder().id(m.getId() != null ? m.getId() : UUID.randomUUID())
                    .restaurantId(m.getRestaurantId()).productId(m.getProductId()).type(m.getType())
                    .direction(m.getDirection()).quantity(m.getQuantity()).unitId(m.getUnitId())
                    .costPerUnit(m.getCostPerUnit()).stockBefore(m.getStockBefore()).stockAfter(m.getStockAfter())
                    .referenceId(m.getReferenceId()).referenceType(m.getReferenceType()).userId(m.getUserId())
                    .source(m.getSource()).build();
        });
        when(productionRepository.save(any())).thenAnswer(i -> {
            Production p = i.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });
        when(stockBatchService.fifoUnitCost(any(), any(), any(), any())).thenAnswer(i -> i.getArgument(3));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private StockConsumptionService consumption() {
        return new StockConsumptionService(productRepository, recipeRepository, productionRepository,
                stockMovementRepository, stockBatchService, unitConversionService, alertEvaluationService,
                foodCostService, auditService);
    }

    private ProductionService productions() {
        return new ProductionService(productionRepository, productRepository, recipeRepository, stockMovementRepository,
                stockBatchRepository, stockBatchService, consumption(), unitConversionService, unitRepository,
                alertEvaluationService, currentUserProvider, auditService);
    }

    private void mayonesaWithStock(String stock) {
        Product mayonesa = new Product();
        mayonesa.setId(mayo);
        mayonesa.setName("Mayonesa base");
        mayonesa.setDefaultUnitId(kg);
        mayonesa.setYieldPercentage(new BigDecimal("100"));
        Product oil = new Product();
        oil.setId(aceite);
        oil.setDefaultUnitId(l);
        oil.setYieldPercentage(new BigDecimal("100"));
        when(productRepository.findByIdAndRestaurantId(mayo, r)).thenReturn(Optional.of(mayonesa));
        when(productRepository.findByIdAndRestaurantId(aceite, r)).thenReturn(Optional.of(oil));
        when(recipeRepository.findByProductIdAndRestaurantId(mayo, r)).thenReturn(Optional.of(Recipe.builder()
                .productId(mayo).restaurantId(r).yieldQuantity(BigDecimal.ONE)
                .items(List.of(RecipeItem.builder().productId(aceite).quantity(new BigDecimal("0.8")).unitId(l).build()))
                .build()));
        when(stockMovementRepository.getCurrentStock(mayo, r)).thenReturn(new BigDecimal(stock));
        when(stockMovementRepository.getCurrentStock(aceite, r)).thenReturn(BigDecimal.TEN);
        when(stockMovementRepository.findLastPurchaseCostPerUnit(aceite, r)).thenReturn(new BigDecimal("2000"));
        when(foodCostService.theoreticalUnitCost(mayo, r, null)).thenReturn(new BigDecimal("1600"));
    }

    @Test
    void sellingAPreparationWithoutEnoughStockProducesWhatIsMissingHangingFromTheSale() {
        mayonesaWithStock("0.2");

        consumption().consume(StockConsumptionService.Consumer.forSale(r, UUID.randomUUID(), saleItemId, null),
                mayo, new BigDecimal("0.5"), kg, new HashSet<>());

        ArgumentCaptor<Production> produced = ArgumentCaptor.forClass(Production.class);
        verify(productionRepository, atLeastOnce()).save(produced.capture());
        Production auto = produced.getValue();
        assertEquals(0, new BigDecimal("0.3").compareTo(auto.getQuantity()), "0,5 que pide la venta − 0,2 que había");
        assertEquals(saleItemId, auto.getSaleItemId());
        assertTrue(auto.isAutomatic());
        assertEquals(0, new BigDecimal("1600").compareTo(auto.getCostPerUnit()), "0,8 l × $2000 por kg");

        ArgumentCaptor<StockMovement> moved = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, atLeastOnce()).save(moved.capture());
        StockMovement oilUse = moved.getAllValues().stream().filter(m -> m.getProductId().equals(aceite)).findFirst().orElseThrow();
        assertEquals(MovementType.PRODUCTION, oilUse.getType());
        assertEquals("production", oilUse.getReferenceType());
        assertEquals(0, new BigDecimal("0.24").compareTo(oilUse.getQuantity()));
        StockMovement sale = moved.getAllValues().stream().filter(m -> m.getType() == MovementType.SALE).findFirst().orElseThrow();
        assertEquals(mayo, sale.getProductId());
        verify(stockBatchService).createProductionBatch(eq(r), eq(mayo), eq(auto.getId()), any(), isNull(), any(), any());
    }

    @Test
    void withEnoughStockNothingIsProduced() {
        mayonesaWithStock("0.6");

        consumption().consume(StockConsumptionService.Consumer.forSale(r, UUID.randomUUID(), saleItemId, null),
                mayo, new BigDecimal("0.5"), kg, new HashSet<>());

        verifyNoInteractions(productionRepository);
    }

    @Test
    void reversingAProductionUndoesItsMovementsThenItsChildren() {
        UUID parentId = UUID.randomUUID(), childId = UUID.randomUUID();
        Production parent = Production.builder().id(parentId).restaurantId(r).productId(mayo).build();
        Production child = Production.builder().id(childId).restaurantId(r).productId(aceite).parentProductionId(parentId).build();
        StockMovement output = movement(mayo, MovementDirection.IN, parentId);
        StockMovement use = movement(aceite, MovementDirection.OUT, parentId);
        StockMovement childOutput = movement(aceite, MovementDirection.IN, childId);
        when(productionRepository.findByIdAndRestaurantId(parentId, r)).thenReturn(Optional.of(parent));
        when(stockMovementRepository.findByReferenceIdAndReferenceType(parentId, "production")).thenReturn(List.of(output, use));
        when(stockMovementRepository.findByReferenceIdAndReferenceType(childId, "production")).thenReturn(List.of(childOutput));
        when(productionRepository.findByParentProductionIdNewestFirst(parentId)).thenReturn(List.of(child));
        when(stockMovementRepository.getCurrentStock(any(), eq(r))).thenReturn(BigDecimal.ONE);

        productions().reverseProduction(parentId);

        ArgumentCaptor<StockMovement> saved = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(3)).save(saved.capture());
        assertEquals(List.of(MovementDirection.OUT, MovementDirection.IN, MovementDirection.OUT),
                saved.getAllValues().stream().map(StockMovement::getDirection).toList(),
                "lo producido sale, el ingrediente vuelve, y después lo de la hija");
        assertTrue(saved.getAllValues().stream().allMatch(m -> m.getType() == MovementType.REVERSAL));
        verify(stockBatchService).reverseAllocations(eq(r), eq(use.getId()), any());
        InOrder order = inOrder(stockBatchRepository);
        order.verify(stockBatchRepository).zeroQuantityRemainingByProductionId(parentId);
        order.verify(stockBatchRepository).zeroQuantityRemainingByProductionId(childId);
        assertEquals(ProductionStatus.REVERSED, parent.getStatus());
        assertEquals(ProductionStatus.REVERSED, child.getStatus());
    }

    @Test
    void aReversedProductionCanNotBeReversedAgain() {
        UUID id = UUID.randomUUID();
        when(productionRepository.findByIdAndRestaurantId(id, r)).thenReturn(Optional.of(
                Production.builder().id(id).restaurantId(r).status(ProductionStatus.REVERSED).build()));

        assertEquals(ErrorCode.PRODUCTION_ALREADY_REVERSED,
                assertThrows(AppException.class, () -> productions().reverseProduction(id)).getErrorCode());
    }

    private StockMovement movement(UUID productId, MovementDirection direction, UUID productionId) {
        return StockMovement.builder().id(UUID.randomUUID()).restaurantId(r).productId(productId)
                .type(MovementType.PRODUCTION).direction(direction).quantity(BigDecimal.ONE).unitId(kg)
                .stockBefore(BigDecimal.ZERO).stockAfter(BigDecimal.ONE).referenceId(productionId)
                .referenceType("production").userId(UUID.randomUUID()).source(MovementSource.DASHBOARD).build();
    }
}
