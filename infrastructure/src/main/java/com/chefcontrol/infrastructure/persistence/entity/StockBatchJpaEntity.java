package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.stock.StockBatch;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "stock_batches")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class StockBatchJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "purchase_item_id")
    private UUID purchaseItemId;

    @Column(name = "quantity_remaining", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityRemaining;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Column(name = "cost_per_unit", precision = 12, scale = 4)
    private BigDecimal costPerUnit;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public static StockBatchJpaEntity from(StockBatch domain) {
        return StockBatchJpaEntity.builder()
                .id(domain.getId())
                .restaurantId(domain.getRestaurantId())
                .productId(domain.getProductId())
                .purchaseItemId(domain.getPurchaseItemId())
                .quantityRemaining(domain.getQuantityRemaining())
                .expirationDate(domain.getExpirationDate())
                .costPerUnit(domain.getCostPerUnit())
                .createdAt(domain.getCreatedAt())
                .build();
    }

    public StockBatch toDomain() {
        return StockBatch.builder()
                .id(id)
                .restaurantId(restaurantId)
                .productId(productId)
                .purchaseItemId(purchaseItemId)
                .quantityRemaining(quantityRemaining)
                .expirationDate(expirationDate)
                .costPerUnit(costPerUnit)
                .createdAt(createdAt)
                .build();
    }
}
