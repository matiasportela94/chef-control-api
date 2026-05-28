package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.stock.MovementDirection;
import com.chefcontrol.domain.stock.MovementSource;
import com.chefcontrol.domain.stock.MovementType;
import com.chefcontrol.domain.stock.StockMovement;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_movements")
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class StockMovementJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovementType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovementDirection direction;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "cost_per_unit", precision = 12, scale = 4)
    private BigDecimal costPerUnit;

    @Column(name = "stock_before", nullable = false, precision = 12, scale = 3)
    private BigDecimal stockBefore;

    @Column(name = "stock_after", nullable = false, precision = 12, scale = 3)
    private BigDecimal stockAfter;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "reference_type", length = 30)
    private String referenceType;

    @Setter
    @Column(name = "reversed_by")
    private UUID reversedBy;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovementSource source;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public static StockMovementJpaEntity from(StockMovement domain) {
        return StockMovementJpaEntity.builder()
                .id(domain.getId())
                .restaurantId(domain.getRestaurantId())
                .productId(domain.getProductId())
                .type(domain.getType())
                .direction(domain.getDirection())
                .quantity(domain.getQuantity())
                .unitId(domain.getUnitId())
                .costPerUnit(domain.getCostPerUnit())
                .stockBefore(domain.getStockBefore())
                .stockAfter(domain.getStockAfter())
                .referenceId(domain.getReferenceId())
                .referenceType(domain.getReferenceType())
                .reversedBy(domain.getReversedBy())
                .userId(domain.getUserId())
                .source(domain.getSource())
                .createdAt(domain.getCreatedAt())
                .build();
    }

    public StockMovement toDomain() {
        return StockMovement.builder()
                .id(id)
                .restaurantId(restaurantId)
                .productId(productId)
                .type(type)
                .direction(direction)
                .quantity(quantity)
                .unitId(unitId)
                .costPerUnit(costPerUnit)
                .stockBefore(stockBefore)
                .stockAfter(stockAfter)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .reversedBy(reversedBy)
                .userId(userId)
                .source(source)
                .createdAt(createdAt)
                .build();
    }
}
