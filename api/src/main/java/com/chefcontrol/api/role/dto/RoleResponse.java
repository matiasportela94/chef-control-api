package com.chefcontrol.api.role.dto;

import com.chefcontrol.domain.user.Role;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        String name,
        boolean isSystem,
        List<String> permissions,
        Instant createdAt
) {
    public static RoleResponse from(Role role) {
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.isSystem(),
                role.effectivePermissions().stream().map(Enum::name).sorted().toList(),
                role.getCreatedAt());
    }
}
