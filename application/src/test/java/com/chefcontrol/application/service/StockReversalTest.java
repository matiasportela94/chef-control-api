package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.product.Unit;
import com.chefcontrol.domain.repository.*;
import com.chefcontrol.domain.stock.*;
import com.chefcontrol.domain.waste.WasteEvent;
import com.chefcontrol.domain.waste.WasteReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Deshacer una merma o un conteo con los servicios de verdad y repositorios en memoria: el stock (suma del ledger) y
 * cada lote tienen que quedar exactamente como estaban.
 */
class StockReversalTest {

    private final UUID r = UUID.randomUUID(), kg = UUID.randomUUID();
    private final UUID tomate = UUID.randomUUID(), comino = UUID.randomUUID();

    private final List<StockMovement> movements = new ArrayList<>();
    private final List<StockBatch> batches = new ArrayList<>();
    private final List<StockBatchAllocation> allocations = new ArrayList<>();
    private final Map<UUID, WasteEvent> wasteEvents = new HashMap<>();
    private final Map<UUID, StockCount> counts = new HashMap<>();
    private final AuditService audit = mock(AuditService.class);

    private WasteService waste;
    private StockCountService stockCounts;

    @BeforeEach
    void setUp() {
        TenantContext.set(r);

        StockMovementRepository movementRepo = mock(StockMovementRepository.class);
        when(movementRepo.save(any())).thenAnswer(i -> {
            StockMovement m = i.<StockMovement>getArgument(0).toBuilder().id(UUID.randomUUID()).build();
            movements.add(m);
            return m;
        });
        when(movementRepo.findByIdAndRestaurantId(any(), eq(r))).thenAnswer(i -> movements.stream()
                .filter(m -> m.getId().equals(i.getArgument(0))).findFirst());
        doAnswer(i -> {
            movements.replaceAll(m -> m.getId().equals(i.getArgument(0)) ? m.toBuilder().reversedBy(i.getArgument(1)).build() : m);
            return null;
        }).when(movementRepo).markReversed(any(), any());
        when(movementRepo.getCurrentStock(any(), eq(r))).thenAnswer(i -> stockOf(i.getArgument(0)));
        when(movementRepo.findByReferenceIdAndReferenceType(any(), any())).thenAnswer(i -> movements.stream()
                .filter(m -> i.getArgument(0).equals(m.getReferenceId()) && i.getArgument(1).equals(m.getReferenceType()))
                .toList());
        when(movementRepo.findLastPurchaseCostPerUnit(any(), eq(r))).thenReturn(new BigDecimal("1000"));

        StockBatchRepository batchRepo = mock(StockBatchRepository.class);
        when(batchRepo.save(any())).thenAnswer(i -> {
            StockBatch b = i.getArgument(0);
            if (b.getId() == null) {
                b.setId(UUID.randomUUID());
                batches.add(b);
            }
            return b;
        });
        when(batchRepo.findByIdAndRestaurantId(any(), eq(r))).thenAnswer(i -> batches.stream()
                .filter(b -> b.getId().equals(i.getArgument(0))).findFirst());
        when(batchRepo.findAvailableByProductFifo(any(), eq(r))).thenAnswer(i -> batches.stream()
                .filter(b -> b.getProductId().equals(i.getArgument(0)) && b.hasRemaining()).toList());

        StockBatchAllocationRepository allocationRepo = mock(StockBatchAllocationRepository.class);
        when(allocationRepo.save(any())).thenAnswer(i -> {
            allocations.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(allocationRepo.findByStockMovementId(any())).thenAnswer(i -> allocations.stream()
                .filter(a -> a.getStockMovementId().equals(i.getArgument(0))).toList());

        WasteEventRepository wasteRepo = mock(WasteEventRepository.class);
        when(wasteRepo.save(any())).thenAnswer(i -> {
            WasteEvent e = i.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            wasteEvents.put(e.getId(), e);
            return e;
        });
        when(wasteRepo.findByIdAndRestaurantId(any(), eq(r))).thenAnswer(i -> Optional.ofNullable(wasteEvents.get(i.getArgument(0))));

        StockCountRepository countRepo = mock(StockCountRepository.class);
        when(countRepo.save(any())).thenAnswer(i -> {
            StockCount c = i.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            counts.put(c.getId(), c);
            return c;
        });
        when(countRepo.findByIdAndRestaurantId(any(), eq(r))).thenAnswer(i -> Optional.ofNullable(counts.get(i.getArgument(0))));

        ProductRepository productRepo = mock(ProductRepository.class);
        for (UUID id : List.of(tomate, comino)) {
            Product p = new Product();
            p.setId(id);
            p.setName(id.equals(tomate) ? "Tomate" : "Comino");
            p.setDefaultUnitId(kg);
            when(productRepo.findByIdAndRestaurantId(id, r)).thenReturn(Optional.of(p));
        }
        UnitRepository unitRepo = mock(UnitRepository.class);
        Unit unit = new Unit();
        unit.setId(kg);
        when(unitRepo.findById(kg)).thenReturn(Optional.of(unit));
        UnitConversionService conversion = mock(UnitConversionService.class);
        when(conversion.convert(any(), any(), any())).thenAnswer(i -> i.getArgument(0));
        AlertEvaluationService alerts = mock(AlertEvaluationService.class);
        CurrentUserProvider user = mock(CurrentUserProvider.class);
        when(user.currentUserId()).thenReturn(UUID.randomUUID());

        StockBatchService batchService = new StockBatchService(batchRepo, allocationRepo);
        StockService stock = new StockService(movementRepo, batchService, alerts, audit, user);
        waste = new WasteService(wasteRepo, movementRepo, productRepo, unitRepo, batchService, stock, conversion, alerts,
                audit, user);
        stockCounts = new StockCountService(countRepo, movementRepo, productRepo, unitRepo, batchService, stock, alerts,
                audit, user);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private BigDecimal stockOf(UUID product) {
        return movements.stream().filter(m -> m.getProductId().equals(product))
                .map(m -> m.getDirection() == MovementDirection.IN ? m.getQuantity() : m.getQuantity().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Una compra: su movimiento y su lote, como los deja PurchaseService. */
    private StockBatch bought(UUID product, String quantity) {
        BigDecimal q = new BigDecimal(quantity);
        movements.add(StockMovement.builder().id(UUID.randomUUID()).restaurantId(r).productId(product)
                .type(MovementType.PURCHASE).direction(MovementDirection.IN).quantity(q).unitId(kg)
                .source(MovementSource.DASHBOARD).build());
        StockBatch b = StockBatch.builder().id(UUID.randomUUID()).restaurantId(r).productId(product)
                .quantityRemaining(q).costPerUnit(new BigDecimal("1000")).build();
        batches.add(b);
        return b;
    }

    private static void assertQty(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    private WasteEvent wasted(UUID product, String quantity) {
        return waste.createWasteEvent(new WasteService.CreateWasteEventCommand(product, kg, new BigDecimal(quantity),
                WasteReason.DAMAGED));
    }

    @Test
    void reversingAWasteGivesBackTheStockAndEachBatchExactly() {
        StockBatch a = bought(tomate, "2"), b = bought(tomate, "5");
        WasteEvent event = wasted(tomate, "3");
        assertQty("0", a.getQuantityRemaining());
        assertQty("4", b.getQuantityRemaining());

        waste.reverseWasteEvent(event.getId());

        assertQty("7", stockOf(tomate));
        assertQty("2", a.getQuantityRemaining());
        assertQty("5", b.getQuantityRemaining());
        verify(audit).log(eq(AuditAction.WASTE_REVERSED), eq("WasteEvent"), eq(event.getId()), any());

        var again = assertThrows(AppException.class, () -> waste.reverseWasteEvent(event.getId()));
        assertEquals(ErrorCode.MOVEMENT_ALREADY_REVERSED, again.getErrorCode());
        assertQty("7", stockOf(tomate));
    }

    @Test
    void reversingACountPutsTheStockBackToBeforeCountingWithTheBatches() {
        StockBatch a = bought(tomate, "2"), b = bought(tomate, "5");
        StockBatch c = bought(comino, "1");
        var count = stockCounts.createStockCount(new StockCountService.CreateStockCountCommand(null, List.of(
                new StockCountService.StockCountItemCommand(tomate, kg, new BigDecimal("4")),     // faltan 3
                new StockCountService.StockCountItemCommand(comino, kg, new BigDecimal("3")))));  // sobran 2
        StockBatch surplus = batches.getLast();
        assertQty("4", stockOf(tomate));
        assertQty("2", surplus.getQuantityRemaining());

        var reversed = stockCounts.reverseStockCount(count.count().getId());

        assertTrue(reversed.reversed());
        assertTrue(reversed.movements().stream().allMatch(m -> m.getReversedBy() != null));
        assertQty("7", stockOf(tomate));
        assertQty("1", stockOf(comino));
        assertQty("2", a.getQuantityRemaining());
        assertQty("5", b.getQuantityRemaining());
        assertQty("1", c.getQuantityRemaining());
        assertQty("0", surplus.getQuantityRemaining());
        assertTrue(stockCounts.getCount(count.count().getId()).reversed());
        verify(audit).log(eq(AuditAction.STOCK_COUNT_REVERSED), eq("StockCount"), eq(count.count().getId()), any());

        var again = assertThrows(AppException.class, () -> stockCounts.reverseStockCount(count.count().getId()));
        assertEquals(ErrorCode.MOVEMENT_ALREADY_REVERSED, again.getErrorCode());
    }

    @Test
    void reversingACountWhoseSurplusWasAlreadyUsedLeavesThatBatchAtZeroNotNegative() {
        StockBatch c = bought(comino, "1");
        var count = stockCounts.createStockCount(new StockCountService.CreateStockCountCommand(null, List.of(
                new StockCountService.StockCountItemCommand(comino, kg, new BigDecimal("3")))));
        StockBatch surplus = batches.getLast();
        wasted(comino, "2");   // el lote de la compra entero y 1 del sobrante
        assertQty("1", surplus.getQuantityRemaining());

        stockCounts.reverseStockCount(count.count().getId());

        assertQty("-1", stockOf(comino));   // 1 comprado − 2 tirados: el ledger no miente
        assertQty("0", c.getQuantityRemaining());
        assertQty("0", surplus.getQuantityRemaining());
    }

    @Test
    void aCountWithoutDifferencesHasNothingToReverse() {
        bought(tomate, "2");
        var count = stockCounts.createStockCount(new StockCountService.CreateStockCountCommand(null, List.of(
                new StockCountService.StockCountItemCommand(tomate, kg, new BigDecimal("2")))));

        var result = stockCounts.reverseStockCount(count.count().getId());

        assertFalse(result.reversed());
        assertQty("2", stockOf(tomate));
        verify(audit, never()).log(eq(AuditAction.STOCK_COUNT_REVERSED), any(), any(), any());
    }
}
