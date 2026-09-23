package com.chefcontrol.api.role.dto;

import com.chefcontrol.domain.user.Permission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record UpdateRoleRequest(
        @NotBlank String name,
        @NotNull Set<Permission> permissions
) {}
