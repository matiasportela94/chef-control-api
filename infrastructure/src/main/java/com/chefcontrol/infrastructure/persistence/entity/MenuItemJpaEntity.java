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

    @Column(name = "section_id")
    private UUID sectionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", insertable = false, updatable = false)
    private MenuSectionJpaEntity section;

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
                .sectionId(domain.getSectionId())
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
                .sectionId(sectionId)
                .sectionName(section != null ? section.getName() : null)
                .sectionColor(section != null ? section.getColor() : null)
                .sectionIcon(section != null ? section.getIcon() : null)
                .sectionSortOrder(section != null ? section.getSortOrder() : null)
                .active(active)
                .createdAt(createdAt)
                .build();
    }
}
