package com.chefcontrol.application.service;

import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.repository.*;
import com.chefcontrol.domain.sale.Sale;
import com.chefcontrol.domain.sale.SaleItem;
import com.chefcontrol.domain.sale.SaleStatus;
import com.chefcontrol.domain.stock.MovementDirection;
import com.chefcontrol.domain.stock.MovementSource;
import com.chefcontrol.domain.stock.MovementType;
import com.chefcontrol.domain.stock.StockMovement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ponytail: un solo caso — revertir una venta tiene que deshacer también las allocations de lote.
 * Sin esto el ledger vuelve a su lugar pero stock_batches.quantity_remaining queda consumido,
 * y la diferencia solo se ve semanas después en un conteo.
 */
@ExtendWith(MockitoExtension.class)
class SaleServiceReversalTest {

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
    private final UUID saleId = UUID.randomUUID();
    private final UUID saleItemId = UUID.randomUUID();
    private final UUID originalMovementId = UUID.randomUUID();
    private final UUID reversalMovementId = UUID.randomUUID();

    @BeforeEach void setTenant() { TenantContext.set(restaurantId); }
    @AfterEach  void clearTenant() { TenantContext.clear(); }

    private SaleService service() {
        return new SaleService(saleRepository, saleItemRepository, menuItemRepository, recipeRepository,
                stockMovementRepository, stockBatchService, alertEvaluationService, auditService,
                currentUserProvider, productRepository, unitConversionService);
    }

    @Test
    void reverseSale_undoesBatchAllocationsOfEveryMovement() {
        Sale sale = Sale.builder().id(saleId).restaurantId(restaurantId).status(SaleStatus.ACTIVE).build();
        when(saleRepository.findByIdAndRestaurantId(saleId, restaurantId)).thenReturn(Optional.of(sale));
        when(saleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(saleItemRepository.findBySaleId(saleId))
                .thenReturn(List.of(SaleItem.builder().id(saleItemId).saleId(saleId).quantity(1).build()));

        StockMovement original = StockMovement.builder()
                .id(originalMovementId).restaurantId(restaurantId).productId(UUID.randomUUID())
                .type(MovementType.SALE).direction(MovementDirection.OUT)
                .quantity(new BigDecimal("0.80")).unitId(UUID.randomUUID())
                .costPerUnit(BigDecimal.TEN)
                .stockBefore(new BigDecimal("10.00")).stockAfter(new BigDecimal("9.20"))
                .referenceId(saleItemId).referenceType("sale_item")
                .userId(UUID.randomUUID()).source(MovementSource.DASHBOARD)
                .build();
        when(stockMovementRepository.findByReferenceIdAndReferenceType(saleItemId, "sale_item"))
                .thenReturn(List.of(original));
        when(stockMovementRepository.getCurrentStock(any(), eq(restaurantId))).thenReturn(new BigDecimal("9.20"));
        when(stockMovementRepository.save(any())).thenAnswer(inv -> {
            StockMovement m = inv.getArgument(0);
            return StockMovement.builder()
                    .id(reversalMovementId).restaurantId(m.getRestaurantId()).productId(m.getProductId())
                    .type(m.getType()).direction(m.getDirection()).quantity(m.getQuantity())
                    .unitId(m.getUnitId()).costPerUnit(m.getCostPerUnit())
                    .stockBefore(m.getStockBefore()).stockAfter(m.getStockAfter())
                    .referenceId(m.getReferenceId()).referenceType(m.getReferenceType())
                    .userId(m.getUserId()).source(m.getSource())
                    .build();
        });

        service().reverseSale(saleId);

        verify(stockBatchService).reverseAllocations(restaurantId, originalMovementId, reversalMovementId);
    }
}
