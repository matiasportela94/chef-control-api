package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.stock.Production;
import com.chefcontrol.domain.stock.ProductionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "productions")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class ProductionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", insertable = false, updatable = false)
    private ProductJpaEntity product;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", insertable = false, updatable = false)
    private UnitJpaEntity unit;

    @Column(name = "cost_per_unit", precision = 12, scale = 4)
    private BigDecimal costPerUnit;

    @Column(name = "total_cost", precision = 12, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Column(name = "sale_item_id")
    private UUID saleItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_item_id", insertable = false, updatable = false)
    private SaleItemJpaEntity saleItem;

    @Column(name = "parent_production_id")
    private UUID parentProductionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductionStatus status;

    private String notes;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "produced_at", nullable = false)
    private Instant producedAt;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (producedAt == null) producedAt = now;
    }

    public static ProductionJpaEntity from(Production d) {
        return ProductionJpaEntity.builder()
                .id(d.getId())
                .restaurantId(d.getRestaurantId())
                .productId(d.getProductId())
                .quantity(d.getQuantity())
                .unitId(d.getUnitId())
                .costPerUnit(d.getCostPerUnit())
                .totalCost(d.getTotalCost())
                .expirationDate(d.getExpirationDate())
                .saleItemId(d.getSaleItemId())
                .parentProductionId(d.getParentProductionId())
                .status(d.getStatus())
                .notes(d.getNotes())
                .userId(d.getUserId())
                .producedAt(d.getProducedAt())
                .createdAt(d.getCreatedAt())
                .build();
    }

    public Production toDomain() {
        return Production.builder()
                .id(id)
                .restaurantId(restaurantId)
                .productId(productId)
                .productName(product != null ? product.getName() : null)
                .quantity(quantity)
                .unitId(unitId)
                .unitName(unit != null ? unit.getName() : null)
                .costPerUnit(costPerUnit)
                .totalCost(totalCost)
                .expirationDate(expirationDate)
                .saleItemId(saleItemId)
                .saleId(saleItem != null ? saleItem.getSaleId() : null)
                .parentProductionId(parentProductionId)
                .status(status)
                .notes(notes)
                .userId(userId)
                .producedAt(producedAt)
                .createdAt(createdAt)
                .build();
    }
}
