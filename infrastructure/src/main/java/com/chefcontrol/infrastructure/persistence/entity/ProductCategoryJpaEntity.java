package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.product.ProductCategory;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_categories")
@Getter @Setter @NoArgsConstructor
public class ProductCategoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_id")
    private UUID restaurantId;

    @Column(nullable = false)
    private String name;

    private String color;

    @Column(name = "is_system", nullable = false)
    private boolean isSystem;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public static ProductCategoryJpaEntity from(ProductCategory domain) {
        ProductCategoryJpaEntity e = new ProductCategoryJpaEntity();
        e.setId(domain.getId());
        e.setRestaurantId(domain.getRestaurantId());
        e.setName(domain.getName());
        e.setColor(domain.getColor());
        e.setSystem(domain.isSystem());
        e.setParentId(domain.getParentId());
        e.setCreatedAt(domain.getCreatedAt());
        return e;
    }

    public ProductCategory toDomain() {
        ProductCategory c = new ProductCategory();
        c.setId(id);
        c.setRestaurantId(restaurantId);
        c.setName(name);
        c.setColor(color);
        c.setSystem(isSystem);
        c.setParentId(parentId);
        c.setCreatedAt(createdAt);
        return c;
    }
}
