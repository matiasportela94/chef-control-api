package com.chefcontrol.application.service;

import com.chefcontrol.domain.repository.StockBatchAllocationRepository;
import com.chefcontrol.domain.repository.StockBatchRepository;
import com.chefcontrol.domain.stock.StockBatch;
import com.chefcontrol.domain.stock.StockBatchAllocation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Tracks per-batch remaining stock for FIFO consumption and expiration control.
 * Every movement that touches batches records {@link StockBatchAllocation} rows so it can be
 * reversed precisely later, regardless of how the FIFO order may have shifted since.
 */
@Service
@RequiredArgsConstructor
public class StockBatchService {

    private final StockBatchRepository stockBatchRepository;
    private final StockBatchAllocationRepository stockBatchAllocationRepository;

    /**
     * Creates a new batch (lot) for stock entering inventory, e.g. from a purchase item or
     * an unexplained surplus found during a stock count.
     */
    @Transactional
    public StockBatch createBatch(UUID restaurantId, UUID productId, UUID purchaseItemId,
                                  BigDecimal quantity, LocalDate expirationDate, BigDecimal costPerUnit,
                                  UUID stockMovementId) {
        StockBatch batch = StockBatch.builder()
                .restaurantId(restaurantId)
                .productId(productId)
                .purchaseItemId(purchaseItemId)
                .quantityRemaining(quantity)
                .expirationDate(expirationDate)
                .costPerUnit(costPerUnit)
                .build();
        batch = stockBatchRepository.save(batch);
        allocate(stockMovementId, batch.getId(), quantity);
        return batch;
    }

    /**
     * Consumes stock FIFO (oldest batch first) across as many batches as needed.
     * If the available batches don't cover the full quantity, consumes what's there and stops —
     * inconsistent historical batch data shouldn't block a sale or waste record.
     */
    @Transactional
    public void consumeFifo(UUID restaurantId, UUID productId, BigDecimal quantity, UUID stockMovementId) {
        BigDecimal pending = quantity;
        for (StockBatch batch : stockBatchRepository.findAvailableByProductFifo(productId, restaurantId)) {
            if (pending.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal taken = batch.consume(pending);
            stockBatchRepository.save(batch);
            allocate(stockMovementId, batch.getId(), taken.negate());
            pending = pending.subtract(taken);
        }
    }

    /**
     * Undoes the batch effects of {@code originalMovementId} by replaying its allocations with
     * the sign inverted, attributing the inverse allocations to {@code reversalMovementId}.
     */
    @Transactional
    public void reverseAllocations(UUID restaurantId, UUID originalMovementId, UUID reversalMovementId) {
        for (StockBatchAllocation original : stockBatchAllocationRepository.findByStockMovementId(originalMovementId)) {
            stockBatchRepository.findByIdAndRestaurantId(original.getStockBatchId(), restaurantId)
                    .ifPresent(batch -> {
                        BigDecimal inverse = original.getQuantity().negate();
                        batch.replenish(inverse);
                        stockBatchRepository.save(batch);
                        allocate(reversalMovementId, batch.getId(), inverse);
                    });
        }
    }

    /**
     * Previews how much a FIFO consumption would cost without touching batch state.
     * Sums costPerUnit × qty_taken across batches in FIFO order.
     */
    public BigDecimal calculateFifoCost(UUID restaurantId, UUID productId, BigDecimal quantity) {
        BigDecimal pending = quantity;
        BigDecimal totalCost = BigDecimal.ZERO;
        for (StockBatch batch : stockBatchRepository.findAvailableByProductFifo(productId, restaurantId)) {
            if (pending.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal taken = pending.min(batch.getQuantityRemaining());
            if (batch.getCostPerUnit() != null) {
                totalCost = totalCost.add(taken.multiply(batch.getCostPerUnit()));
            }
            pending = pending.subtract(taken);
        }
        return totalCost.setScale(2, RoundingMode.HALF_UP);
    }

    private void allocate(UUID stockMovementId, UUID stockBatchId, BigDecimal quantity) {
        stockBatchAllocationRepository.save(StockBatchAllocation.builder()
                .stockMovementId(stockMovementId)
                .stockBatchId(stockBatchId)
                .quantity(quantity)
                .build());
    }
}
