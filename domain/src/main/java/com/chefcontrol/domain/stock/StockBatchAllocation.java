package com.chefcontrol.domain.stock;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Records how much a stock movement added to or removed from a specific batch.
 * Positive quantity = the batch gained stock (purchase, reversal of an outbound movement).
 * Negative quantity = the batch lost stock (sale, waste, adjustment, reversal of an inbound movement).
 */
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class StockBatchAllocation {

    private UUID id;
    private UUID stockMovementId;
    private UUID stockBatchId;
    private BigDecimal quantity;
    private Instant createdAt;
}
