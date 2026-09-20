package com.chefcontrol.api.user.dto;

import com.chefcontrol.domain.user.UserRestaurant;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        String phone,
        UUID roleId,
        String role,
        boolean roleIsSystem,
        boolean isActive,
        Instant memberSince
) {
    public static UserResponse from(UserRestaurant membership) {
        return new UserResponse(
                membership.getUserId(),
                membership.getUserName(),
                membership.getUserEmail(),
                membership.getUserPhone(),
                membership.getRoleId(),
                membership.getRoleName(),
                membership.isRoleIsSystem(),
                membership.isActive(),
                membership.getCreatedAt());
    }
}
