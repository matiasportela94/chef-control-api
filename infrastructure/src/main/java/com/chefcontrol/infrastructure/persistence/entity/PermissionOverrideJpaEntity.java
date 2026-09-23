package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.user.Permission;
import com.chefcontrol.domain.user.PermissionOverride;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "permission_overrides")
@Getter @Setter @NoArgsConstructor
public class PermissionOverrideJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Permission permission;

    @Column(nullable = false)
    private boolean granted;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public static PermissionOverrideJpaEntity from(PermissionOverride domain) {
        PermissionOverrideJpaEntity e = new PermissionOverrideJpaEntity();
        e.setId(domain.getId());
        e.setUserId(domain.getUserId());
        e.setRestaurantId(domain.getRestaurantId());
        e.setPermission(domain.getPermission());
        e.setGranted(domain.isGranted());
        e.setCreatedAt(domain.getCreatedAt());
        return e;
    }

    public PermissionOverride toDomain() {
        return PermissionOverride.builder()
                .id(id)
                .userId(userId)
                .restaurantId(restaurantId)
                .permission(permission)
                .granted(granted)
                .createdAt(createdAt)
                .build();
    }
}
