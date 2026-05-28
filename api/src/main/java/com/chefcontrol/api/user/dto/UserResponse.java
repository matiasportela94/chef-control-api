package com.chefcontrol.api.user.dto;

import com.chefcontrol.domain.user.UserRestaurant;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        String phone,
        String role,
        boolean isActive,
        Instant memberSince
) {
    public static UserResponse from(UserRestaurant membership) {
        return new UserResponse(
                membership.getUserId(),
                membership.getUserName(),
                membership.getUserEmail(),
                membership.getUserPhone(),
                membership.getRoleName().name(),
                membership.isActive(),
                membership.getCreatedAt());
    }
}
