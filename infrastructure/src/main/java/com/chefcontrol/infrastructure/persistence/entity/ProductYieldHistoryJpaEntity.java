package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.history.PriceHistoryEntry;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_yield_history")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class ProductYieldHistoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "yield_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal yieldPercentage;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "changed_by")
    private UUID changedBy;

    public static ProductYieldHistoryJpaEntity from(PriceHistoryEntry domain) {
        return ProductYieldHistoryJpaEntity.builder()
                .id(domain.id())
                .restaurantId(domain.restaurantId())
                .productId(domain.entityId())
                .yieldPercentage(domain.value())
                .validFrom(domain.validFrom())
                .changedBy(domain.changedBy())
                .build();
    }

    public PriceHistoryEntry toDomain() {
        return new PriceHistoryEntry(id, restaurantId, productId, yieldPercentage, validFrom, changedBy);
    }
}
