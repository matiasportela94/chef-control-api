package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.plan.PlanTier;
import com.chefcontrol.domain.restaurant.Restaurant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "restaurants")
@Getter @Setter @NoArgsConstructor
public class RestaurantJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String slug;

    @Column(nullable = false)
    private String timezone = "America/Argentina/Buenos_Aires";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanTier plan = PlanTier.TRIAL;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public static RestaurantJpaEntity from(Restaurant domain) {
        RestaurantJpaEntity e = new RestaurantJpaEntity();
        e.setId(domain.getId());
        e.setName(domain.getName());
        e.setSlug(domain.getSlug());
        e.setTimezone(domain.getTimezone());
        e.setPlan(domain.getPlan());
        e.setActive(domain.isActive());
        e.setCreatedAt(domain.getCreatedAt());
        return e;
    }

    public Restaurant toDomain() {
        Restaurant r = new Restaurant();
        r.setId(id);
        r.setName(name);
        r.setSlug(slug);
        r.setTimezone(timezone);
        r.setPlan(plan);
        r.setActive(isActive);
        r.setCreatedAt(createdAt);
        return r;
    }
}
