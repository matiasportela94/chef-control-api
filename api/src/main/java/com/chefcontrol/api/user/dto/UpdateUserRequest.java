package com.chefcontrol.api.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateUserRequest(
        @NotBlank String name,
        String phone,
        @NotNull UUID roleId
) {}
