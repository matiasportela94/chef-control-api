package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.purchase.PurchaseItem;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "purchase_items")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class PurchaseItemJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "purchase_id", nullable = false)
    private UUID purchaseId;

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

    @Column(name = "price_per_unit", nullable = false, precision = 12, scale = 4)
    private BigDecimal pricePerUnit;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public static PurchaseItemJpaEntity from(PurchaseItem domain) {
        return PurchaseItemJpaEntity.builder()
                .id(domain.getId())
                .purchaseId(domain.getPurchaseId())
                .productId(domain.getProductId())
                .quantity(domain.getQuantity())
                .unitId(domain.getUnitId())
                .pricePerUnit(domain.getPricePerUnit())
                .createdAt(domain.getCreatedAt())
                .build();
    }

    public PurchaseItem toDomain() {
        return PurchaseItem.builder()
                .id(id)
                .purchaseId(purchaseId)
                .productId(productId)
                .productName(product != null ? product.getName() : null)
                .productSku(product != null ? product.getSku() : null)
                .quantity(quantity)
                .unitId(unitId)
                .unitName(unit != null ? unit.getName() : null)
                .unitAbbreviation(unit != null ? unit.getAbbreviation() : null)
                .pricePerUnit(pricePerUnit)
                .createdAt(createdAt)
                .build();
    }
}
