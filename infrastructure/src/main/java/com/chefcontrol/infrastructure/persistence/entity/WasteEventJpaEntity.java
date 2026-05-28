package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.waste.WasteEvent;
import com.chefcontrol.domain.waste.WasteReason;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "waste_events")
@Getter @Setter @NoArgsConstructor
public class WasteEventJpaEntity {

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

    @Enumerated(EnumType.STRING)
    @Column(length = 255)
    private WasteReason reason;

    @Column(precision = 12, scale = 2)
    private BigDecimal cost;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public static WasteEventJpaEntity from(WasteEvent domain) {
        WasteEventJpaEntity e = new WasteEventJpaEntity();
        e.setId(domain.getId());
        e.setRestaurantId(domain.getRestaurantId());
        e.setProductId(domain.getProductId());
        e.setQuantity(domain.getQuantity());
        e.setUnitId(domain.getUnitId());
        e.setReason(domain.getReason());
        e.setCost(domain.getCost());
        e.setUserId(domain.getUserId());
        e.setCreatedAt(domain.getCreatedAt());
        return e;
    }

    public WasteEvent toDomain() {
        WasteEvent w = new WasteEvent();
        w.setId(id);
        w.setRestaurantId(restaurantId);
        w.setProductId(productId);
        w.setProductName(product != null ? product.getName() : null);
        w.setProductSku(product != null ? product.getSku() : null);
        w.setQuantity(quantity);
        w.setUnitId(unitId);
        w.setUnitName(unit != null ? unit.getName() : null);
        w.setUnitAbbreviation(unit != null ? unit.getAbbreviation() : null);
        w.setReason(reason);
        w.setCost(cost);
        w.setUserId(userId);
        w.setCreatedAt(createdAt);
        return w;
    }
}
