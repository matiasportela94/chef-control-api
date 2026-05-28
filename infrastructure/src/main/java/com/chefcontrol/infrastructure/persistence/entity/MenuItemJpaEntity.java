package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.menu.MenuItem;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "menu_items")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class MenuItemJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    private String category;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public static MenuItemJpaEntity from(MenuItem domain) {
        return MenuItemJpaEntity.builder()
                .id(domain.getId())
                .restaurantId(domain.getRestaurantId())
                .name(domain.getName())
                .description(domain.getDescription())
                .price(domain.getPrice())
                .category(domain.getCategory())
                .active(domain.isActive())
                .createdAt(domain.getCreatedAt())
                .build();
    }

    public MenuItem toDomain() {
        return MenuItem.builder()
                .id(id)
                .restaurantId(restaurantId)
                .name(name)
                .description(description)
                .price(price)
                .category(category)
                .active(active)
                .createdAt(createdAt)
                .build();
    }
}
