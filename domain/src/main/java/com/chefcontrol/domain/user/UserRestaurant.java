package com.chefcontrol.domain.user;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class UserRestaurant {

    private UUID userId;
    private UUID restaurantId;

    // Denormalized from role join
    private UUID roleId;
    private String roleName;
    private boolean roleIsSystem;

    // Denormalized from restaurant join
    private String restaurantName;
    private String restaurantSlug;
    private String restaurantTimezone;

    // Denormalized from user join (populated when querying by restaurant)
    private String userEmail;
    private String userName;
    private String userPhone;

    private boolean isActive = true;
    private Instant createdAt;

    public void deactivate() {
        this.isActive = false;
    }
}
