package com.chefcontrol.domain.stock;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class StockBatch {

    private UUID id;
    private UUID restaurantId;
    private UUID productId;
    private UUID purchaseItemId;
    private BigDecimal quantityRemaining;
    private LocalDate expirationDate;
    private BigDecimal costPerUnit;
    private Instant createdAt;

    /**
     * Consumes up to {@code quantity} from this batch and returns how much was actually taken
     * (clamped to what remains, never leaving the batch negative).
     */
    public BigDecimal consume(BigDecimal quantity) {
        BigDecimal taken = quantity.min(quantityRemaining);
        this.quantityRemaining = this.quantityRemaining.subtract(taken);
        return taken;
    }

    public void replenish(BigDecimal quantity) {
        this.quantityRemaining = this.quantityRemaining.add(quantity);
    }

    public boolean hasRemaining() {
        return quantityRemaining.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isExpired(LocalDate referenceDate) {
        return expirationDate != null && expirationDate.isBefore(referenceDate);
    }
}
