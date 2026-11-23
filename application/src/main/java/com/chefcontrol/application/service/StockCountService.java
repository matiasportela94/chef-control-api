package com.chefcontrol.application.service;

import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.repository.*;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.domain.stock.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockCountService {

    private final StockCountRepository stockCountRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final UnitRepository unitRepository;
    private final StockBatchService stockBatchService;
    private final StockService stockService;
    private final AlertEvaluationService alertEvaluationService;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;

    public Page<StockCount> listCounts(PageRequest pageRequest) {
        return stockCountRepository.findByRestaurantIdOrderByCountedAtDesc(TenantContext.require(), pageRequest);
    }

    /** De esos conteos, los revertidos (el listado no trae los movimientos). */
    public java.util.Set<UUID> findReversedStockCountIds(java.util.Collection<UUID> ids) {
        return stockCountRepository.findReversedIds(ids);
    }

    public StockCountResult getCount(UUID id) {
        UUID restaurantId = TenantContext.require();
        StockCount count = stockCountRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.STOCK_COUNT_NOT_FOUND, "Stock count not found"));
        List<StockMovement> movements = stockMovementRepository
                .findByReferenceIdAndReferenceType(count.getId(), "stock_count");
        return new StockCountResult(count, movements);
    }

    @Transactional
    public StockCountResult createStockCount(CreateStockCountCommand cmd) {
        UUID restaurantId = TenantContext.require();
        UUID userId = currentUserProvider.currentUserId();

        StockCount count = new StockCount();
        count.setRestaurantId(restaurantId);
        count.setUserId(userId);
        count.setNotes(cmd.notes());
        count = stockCountRepository.save(count);

        List<StockMovement> movements = new ArrayList<>();

        for (StockCountItemCommand item : cmd.items()) {
            Product product = productRepository.findByIdAndRestaurantId(item.productId(), restaurantId)
                    .filter(Product::isActive)
                    .orElseThrow(() -> AppException.notFound(ErrorCode.PRODUCT_NOT_FOUND,
                            "Product not found: " + item.productId()));

            var unit = unitRepository.findById(item.unitId())
                    .orElseThrow(() -> AppException.notFound(ErrorCode.UNIT_NOT_FOUND,
                            "Unit not found: " + item.unitId()));

            BigDecimal currentStock = stockMovementRepository.getCurrentStock(product.getId(), restaurantId);

            if (item.countedQuantity().compareTo(currentStock) == 0) continue;

            StockMovement movement = StockMovement.forAdjustment(
                    restaurantId, product.getId(),
                    currentStock, item.countedQuantity(),
                    unit.getId(), count.getId(), userId);
            movement = stockMovementRepository.save(movement);

            if (movement.getDirection() == MovementDirection.OUT) {
                stockBatchService.consumeFifo(restaurantId, product.getId(), movement.getQuantity(), movement.getId());
            } else {
                // Surplus found during a physical count: use last purchase price as cost estimate
                // so downstream FIFO cost calculations (waste, food cost) have a reference value.
                BigDecimal lastCost = stockMovementRepository.findLastPurchaseCostPerUnit(product.getId(), restaurantId);
                stockBatchService.createBatch(restaurantId, product.getId(), null,
                        movement.getQuantity(), null, lastCost, movement.getId());
            }

            movements.add(movement);
            alertEvaluationService.evaluate(product.getId(), restaurantId, movement.getStockAfter());
        }

        count.setItemsChecked(cmd.items().size());
        count.setAdjustmentsMade(movements.size());
        count = stockCountRepository.save(count);

        auditService.log(AuditAction.STOCK_COUNT_CREATED, "StockCount", count.getId(),
                Map.of("adjustments", movements.size(), "itemsChecked", cmd.items().size()));

        return new StockCountResult(count, movements);
    }

    /**
     * Devuelve el stock a como estaba antes de contar: revierte cada ajuste que siga en pie, y los lotes se mueven con él
     * (lo que consumió vuelve; el sobrante que creó sale). Un conteo sin diferencias no movió nada: no hay qué revertir.
     * Ya revertido → 409 MOVEMENT_ALREADY_REVERSED.
     */
    @Transactional
    public StockCountResult reverseStockCount(UUID id) {
        StockCountResult current = getCount(id);
        if (current.reversed()) {
            throw AppException.conflict(ErrorCode.MOVEMENT_ALREADY_REVERSED, "Stock count already reversed");
        }
        // Las copias llevan su reversión: releerlos en la misma transacción los traería sin ella (markReversed es un UPDATE).
        List<StockMovement> movements = current.movements().stream()
                .map(m -> m.getReversedBy() != null ? m
                        : m.toBuilder().reversedBy(stockService.reverseMovement(m.getId()).getId()).build())
                .toList();
        if (!movements.isEmpty()) {
            auditService.log(AuditAction.STOCK_COUNT_REVERSED, "StockCount", id, Map.of("adjustments", movements.size()));
        }
        return new StockCountResult(current.count(), movements);
    }

    // ── Commands / Results ────────────────────────────────────────────────────

    public record CreateStockCountCommand(String notes, List<StockCountItemCommand> items) {}

    public record StockCountItemCommand(UUID productId, UUID unitId, BigDecimal countedQuantity) {}

    public record StockCountResult(StockCount count, List<StockMovement> movements) {
        /** Tenía ajustes y no queda ninguno en pie. */
        public boolean reversed() {
            return !movements.isEmpty() && movements.stream().allMatch(m -> m.getReversedBy() != null);
        }
    }
}
