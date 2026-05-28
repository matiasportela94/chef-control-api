package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.alert.Alert;
import com.chefcontrol.domain.alert.AlertSeverity;
import com.chefcontrol.domain.alert.AlertType;
import com.chefcontrol.infrastructure.persistence.entity.converter.AlertSeverityConverter;
import com.chefcontrol.infrastructure.persistence.entity.converter.AlertTypeConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alerts")
@Getter @Setter @NoArgsConstructor
public class AlertJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "product_id")
    private UUID productId;

    @Convert(converter = AlertTypeConverter.class)
    @Column(nullable = false)
    private AlertType type;

    @Convert(converter = AlertSeverityConverter.class)
    @Column(nullable = false)
    private AlertSeverity severity;

    @Column(nullable = false)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public static AlertJpaEntity from(Alert domain) {
        AlertJpaEntity e = new AlertJpaEntity();
        e.setId(domain.getId());
        e.setRestaurantId(domain.getRestaurantId());
        e.setProductId(domain.getProductId());
        e.setType(domain.getType());
        e.setSeverity(domain.getSeverity());
        e.setMessage(domain.getMessage());
        e.setRead(domain.isRead());
        e.setResolvedAt(domain.getResolvedAt());
        e.setCreatedAt(domain.getCreatedAt());
        return e;
    }

    public Alert toDomain() {
        Alert a = new Alert();
        a.setId(id);
        a.setRestaurantId(restaurantId);
        a.setProductId(productId);
        a.setType(type);
        a.setSeverity(severity);
        a.setMessage(message);
        a.setRead(isRead);
        a.setResolvedAt(resolvedAt);
        a.setCreatedAt(createdAt);
        return a;
    }
}
