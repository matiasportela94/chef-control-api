package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.stock.StockBatchAllocation;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_movement_batch_allocations")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class StockBatchAllocationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "stock_movement_id", nullable = false)
    private UUID stockMovementId;

    @Column(name = "stock_batch_id", nullable = false)
    private UUID stockBatchId;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public static StockBatchAllocationJpaEntity from(StockBatchAllocation domain) {
        return StockBatchAllocationJpaEntity.builder()
                .id(domain.getId())
                .stockMovementId(domain.getStockMovementId())
                .stockBatchId(domain.getStockBatchId())
                .quantity(domain.getQuantity())
                .createdAt(domain.getCreatedAt())
                .build();
    }

    public StockBatchAllocation toDomain() {
        return StockBatchAllocation.builder()
                .id(id)
                .stockMovementId(stockMovementId)
                .stockBatchId(stockBatchId)
                .quantity(quantity)
                .createdAt(createdAt)
                .build();
    }
}
