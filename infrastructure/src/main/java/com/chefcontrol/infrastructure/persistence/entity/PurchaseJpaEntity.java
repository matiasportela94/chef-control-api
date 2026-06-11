package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.purchase.Purchase;
import com.chefcontrol.domain.purchase.PurchaseStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "purchases")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class PurchaseJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", insertable = false, updatable = false)
    private SupplierJpaEntity supplier;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(precision = 12, scale = 2)
    private BigDecimal total;

    private String notes;

    @Column(name = "purchased_at", nullable = false)
    private Instant purchasedAt;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PurchaseStatus status = PurchaseStatus.ACTIVE;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public static PurchaseJpaEntity from(Purchase domain) {
        return PurchaseJpaEntity.builder()
                .id(domain.getId())
                .restaurantId(domain.getRestaurantId())
                .supplierId(domain.getSupplierId())
                .userId(domain.getUserId())
                .total(domain.getTotal())
                .notes(domain.getNotes())
                .purchasedAt(domain.getPurchasedAt())
                .createdAt(domain.getCreatedAt())
                .status(domain.getStatus() != null ? domain.getStatus() : PurchaseStatus.ACTIVE)
                .build();
    }

    public Purchase toDomain() {
        return Purchase.builder()
                .id(id)
                .restaurantId(restaurantId)
                .supplierId(supplierId)
                .supplierName(supplier != null ? supplier.getName() : null)
                .userId(userId)
                .total(total)
                .notes(notes)
                .purchasedAt(purchasedAt)
                .createdAt(createdAt)
                .status(status)
                .build();
    }
}
