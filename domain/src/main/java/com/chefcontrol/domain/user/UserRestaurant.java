package com.chefcontrol.domain.user;

import com.chefcontrol.domain.plan.PlanTier;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class UserRestaurant {

    private UUID userId;
    private UUID restaurantId;

    // Denormalized from role join
    private UUID roleId;
    private RoleName roleName;

    // Denormalized from restaurant join
    private String restaurantName;
    private String restaurantSlug;
    private String restaurantTimezone;
    private PlanTier restaurantPlan;

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
