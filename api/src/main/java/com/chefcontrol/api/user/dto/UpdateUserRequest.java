package com.chefcontrol.api.user.dto;

import com.chefcontrol.domain.user.RoleName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRequest(
        @NotBlank String name,
        String phone,
        @NotNull RoleName role
) {}
