package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.user.UserRestaurant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "user_restaurants")
@Getter @Setter @NoArgsConstructor
public class UserRestaurantJpaEntity {

    @EmbeddedId
    private UserRestaurantJpaId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private UserJpaEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("restaurantId")
    @JoinColumn(name = "restaurant_id")
    private RestaurantJpaEntity restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private RoleJpaEntity role;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UserRestaurant toDomain() {
        return UserRestaurant.builder()
                .userId(id.getUserId())
                .restaurantId(id.getRestaurantId())
                .roleId(role != null ? role.getId() : null)
                .roleName(role != null ? role.getName() : null)
                .restaurantName(restaurant != null ? restaurant.getName() : null)
                .restaurantSlug(restaurant != null ? restaurant.getSlug() : null)
                .restaurantTimezone(restaurant != null ? restaurant.getTimezone() : null)
                .restaurantPlan(restaurant != null ? restaurant.getPlan() : null)
                .userEmail(user != null ? user.getEmail() : null)
                .userName(user != null ? user.getName() : null)
                .userPhone(user != null ? user.getPhone() : null)
                .isActive(isActive)
                .createdAt(createdAt)
                .build();
    }
}
