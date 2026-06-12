package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.sale.Sale;
import com.chefcontrol.domain.sale.SaleStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sales")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class SaleJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 10)
    private String source;

    private String notes;

    @Column(name = "sold_at", nullable = false)
    private Instant soldAt;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaleStatus status = SaleStatus.ACTIVE;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public static SaleJpaEntity from(Sale domain) {
        return SaleJpaEntity.builder()
                .id(domain.getId())
                .restaurantId(domain.getRestaurantId())
                .userId(domain.getUserId())
                .totalAmount(domain.getTotalAmount())
                .source(domain.getSource())
                .notes(domain.getNotes())
                .soldAt(domain.getSoldAt())
                .createdAt(domain.getCreatedAt())
                .status(domain.getStatus() != null ? domain.getStatus() : SaleStatus.ACTIVE)
                .build();
    }

    public Sale toDomain() {
        return Sale.builder()
                .id(id)
                .restaurantId(restaurantId)
                .userId(userId)
                .totalAmount(totalAmount)
                .source(source)
                .notes(notes)
                .soldAt(soldAt)
                .createdAt(createdAt)
                .status(status)
                .build();
    }
}
