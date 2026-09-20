package com.chefcontrol.api.user.dto;

import com.chefcontrol.application.service.UserManagementService.EffectivePermissions;

import java.util.List;
import java.util.UUID;

public record UserPermissionsResponse(
        UUID roleId,
        String roleName,
        boolean roleIsSystem,
        List<String> roleDefaults,
        List<PermissionOverrideDto> overrides,
        List<String> effective
) {
    public record PermissionOverrideDto(String permission, boolean granted) {}

    public static UserPermissionsResponse from(EffectivePermissions p) {
        return new UserPermissionsResponse(
                p.role().getId(),
                p.role().getName(),
                p.role().isSystem(),
                p.role().effectivePermissions().stream().map(Enum::name).toList(),
                p.overrides().stream()
                        .map(o -> new PermissionOverrideDto(o.getPermission().name(), o.isGranted()))
                        .toList(),
                p.effective().stream().map(Enum::name).toList());
    }
}
