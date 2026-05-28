package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.product.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter @Setter @NoArgsConstructor
public class ProductJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "category_id")
    private UUID categoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", insertable = false, updatable = false)
    private ProductCategoryJpaEntity category;

    @Column(nullable = false)
    private String name;

    private String sku;

    @Column(name = "default_unit_id", nullable = false)
    private UUID defaultUnitId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_unit_id", insertable = false, updatable = false)
    private UnitJpaEntity defaultUnit;

    @Column(name = "min_stock", precision = 12, scale = 3)
    private BigDecimal minStock;

    @Column(name = "max_stock", precision = 12, scale = 3)
    private BigDecimal maxStock;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public static ProductJpaEntity from(Product domain) {
        ProductJpaEntity e = new ProductJpaEntity();
        e.setId(domain.getId());
        e.setRestaurantId(domain.getRestaurantId());
        e.setCategoryId(domain.getCategoryId());
        e.setName(domain.getName());
        e.setSku(domain.getSku());
        e.setDefaultUnitId(domain.getDefaultUnitId());
        e.setMinStock(domain.getMinStock());
        e.setMaxStock(domain.getMaxStock());
        e.setActive(domain.isActive());
        e.setCreatedAt(domain.getCreatedAt());
        return e;
    }

    public Product toDomain() {
        Product p = new Product();
        p.setId(id);
        p.setRestaurantId(restaurantId);
        p.setCategoryId(categoryId);
        p.setCategoryName(category != null ? category.getName() : null);
        p.setCategoryColor(category != null ? category.getColor() : null);
        p.setName(name);
        p.setSku(sku);
        p.setDefaultUnitId(defaultUnitId);
        p.setDefaultUnitName(defaultUnit != null ? defaultUnit.getName() : null);
        p.setDefaultUnitAbbreviation(defaultUnit != null ? defaultUnit.getAbbreviation() : null);
        p.setMinStock(minStock);
        p.setMaxStock(maxStock);
        p.setActive(isActive);
        p.setCreatedAt(createdAt);
        return p;
    }
}
