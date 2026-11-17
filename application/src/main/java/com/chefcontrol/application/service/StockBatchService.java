package com.chefcontrol.application.service;

import com.chefcontrol.domain.repository.StockBatchAllocationRepository;
import com.chefcontrol.domain.repository.StockBatchRepository;
import com.chefcontrol.domain.stock.StockBatch;
import com.chefcontrol.domain.stock.StockBatchAllocation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chefcontrol.domain.context.TenantContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
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
    /**
     * Para el listado de insumos: qué vence primero en cada producto, mirando solo lotes con
     * stock remanente. Sin ventana de días — la pantalla muestra la fecha y el que mira decide,
     * en vez de esperar a que el barrido nocturno levante la alerta.
     */
    public Map<UUID, LocalDate> getNextExpirationDates() {
        return stockBatchRepository.findNextExpirationByProduct(TenantContext.require());
    }

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
     * Lo que cuesta por unidad consumir {@code quantity} ahora, sin tocar los lotes: lo que tomaría de cada lote en
     * orden FIFO, a lo que costó ese lote, y lo que los lotes no cubren (stock negativo, o un lote sin costo, como el
     * sobrante de un conteo sin compras) a {@code fallbackCost}. Null = 0. Se llama justo antes de
     * {@link #consumeFifo}, en la misma transacción, así que mira los mismos lotes que se van a consumir.
     */
    public BigDecimal fifoUnitCost(UUID restaurantId, UUID productId, BigDecimal quantity, BigDecimal fallbackCost) {
        BigDecimal fallback = fallbackCost != null ? fallbackCost : BigDecimal.ZERO;
        if (quantity.signum() <= 0) return fallback;
        BigDecimal pending = quantity;
        BigDecimal totalCost = BigDecimal.ZERO;
        for (StockBatch batch : stockBatchRepository.findAvailableByProductFifo(productId, restaurantId)) {
            if (pending.signum() <= 0) break;
            BigDecimal taken = pending.min(batch.getQuantityRemaining());
            totalCost = totalCost.add(taken.multiply(batch.getCostPerUnit() != null ? batch.getCostPerUnit() : fallback));
            pending = pending.subtract(taken);
        }
        totalCost = totalCost.add(pending.max(BigDecimal.ZERO).multiply(fallback));
        return totalCost.divide(quantity, 4, RoundingMode.HALF_UP); // la precisión de cost_per_unit
    }

    private void allocate(UUID stockMovementId, UUID stockBatchId, BigDecimal quantity) {
        stockBatchAllocationRepository.save(StockBatchAllocation.builder()
                .stockMovementId(stockMovementId)
                .stockBatchId(stockBatchId)
                .quantity(quantity)
                .build());
    }
}
