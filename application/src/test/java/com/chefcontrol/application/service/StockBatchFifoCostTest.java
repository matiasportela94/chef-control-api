package com.chefcontrol.application.service;

import com.chefcontrol.domain.repository.StockBatchAllocationRepository;
import com.chefcontrol.domain.repository.StockBatchRepository;
import com.chefcontrol.domain.stock.StockBatch;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * El costo realizado de un consumo: lo que se toma de cada lote FIFO a lo que costó, y lo que los lotes no cubren a
 * la última compra. Sin tocar los lotes: eso lo hace {@code consumeFifo} después.
 */
class StockBatchFifoCostTest {

    private final UUID restaurantId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final StockBatchRepository batches = mock(StockBatchRepository.class);
    private final StockBatchService service = new StockBatchService(batches, mock(StockBatchAllocationRepository.class));

    private BigDecimal costOf(String quantity, String fallback, StockBatch... fifo) {
        when(batches.findAvailableByProductFifo(productId, restaurantId)).thenReturn(List.of(fifo));
        return service.fifoUnitCost(restaurantId, productId, new BigDecimal(quantity),
                fallback == null ? null : new BigDecimal(fallback));
    }

    private static StockBatch batch(String remaining, String cost) {
        return StockBatch.builder().quantityRemaining(new BigDecimal(remaining))
                .costPerUnit(cost == null ? null : new BigDecimal(cost)).build();
    }

    @Test
    void oneBatchCoversItAll_atThatBatchCost() {
        // la última compra fue a 2000, pero lo que se consume es el lote viejo de 1000
        assertThat(costOf("3", "2000", batch("10", "1000"))).isEqualByComparingTo("1000");
    }

    @Test
    void crossingTwoBatches_isTheAverageOfWhatWasConsumed() {
        // 2 kg a 1000 + 1 kg a 2000 = 4000 / 3 kg
        assertThat(costOf("3", "2000", batch("2", "1000"), batch("10", "2000"))).isEqualByComparingTo("1333.3333");
    }

    @Test
    void whatTheBatchesDontCover_goesAtTheFallback() {
        // 1 kg en lotes a 1000 + 3 kg sin lote a 2000 = 7000 / 4 kg
        assertThat(costOf("4", "2000", batch("1", "1000"))).isEqualByComparingTo("1750");
        assertThat(costOf("4", "2000")).isEqualByComparingTo("2000");
    }

    @Test
    void aBatchWithoutCost_goesAtTheFallback() {
        // el sobrante de un conteo sin compras: no es gratis
        assertThat(costOf("2", "2000", batch("1", null), batch("5", "1000"))).isEqualByComparingTo("1500");
    }

    @Test
    void noFallback_meansZeroForTheUncoveredPart_andTheBatchesAreNotTouched() {
        assertThat(costOf("2", null, batch("1", "1000"))).isEqualByComparingTo("500");
        verify(batches, never()).save(any());
    }
}
