package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.menu.MenuSection;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "menu_sections")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class MenuSectionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    private String color;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public static MenuSectionJpaEntity from(MenuSection domain) {
        return MenuSectionJpaEntity.builder()
                .id(domain.getId())
                .restaurantId(domain.getRestaurantId())
                .name(domain.getName())
                .sortOrder(domain.getSortOrder())
                .color(domain.getColor())
                .createdAt(domain.getCreatedAt())
                .build();
    }

    public MenuSection toDomain() {
        return MenuSection.builder()
                .id(id)
                .restaurantId(restaurantId)
                .name(name)
                .sortOrder(sortOrder)
                .color(color)
                .createdAt(createdAt)
                .build();
    }
}
