package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.restaurant.Restaurant;
import com.chefcontrol.domain.restaurant.TaxCondition;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "restaurants")
@Getter @Setter @NoArgsConstructor
public class RestaurantJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String slug;

    @Column(nullable = false)
    private String timezone = "America/Argentina/Buenos_Aires";

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_condition")
    private TaxCondition taxCondition;

    @Column(name = "sales_iva_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal salesIvaPercentage = new BigDecimal("21");

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public static RestaurantJpaEntity from(Restaurant domain) {
        RestaurantJpaEntity e = new RestaurantJpaEntity();
        e.setId(domain.getId());
        e.setAccountId(domain.getAccountId());
        e.setName(domain.getName());
        e.setSlug(domain.getSlug());
        e.setTimezone(domain.getTimezone());
        e.setActive(domain.isActive());
        e.setTaxCondition(domain.getTaxCondition());
        e.setSalesIvaPercentage(domain.getSalesIvaPercentage());
        e.setCreatedAt(domain.getCreatedAt());
        return e;
    }

    public Restaurant toDomain() {
        Restaurant r = new Restaurant();
        r.setId(id);
        r.setAccountId(accountId);
        r.setName(name);
        r.setSlug(slug);
        r.setTimezone(timezone);
        r.setActive(isActive);
        r.setTaxCondition(taxCondition);
        r.setSalesIvaPercentage(salesIvaPercentage);
        r.setCreatedAt(createdAt);
        return r;
    }
}
