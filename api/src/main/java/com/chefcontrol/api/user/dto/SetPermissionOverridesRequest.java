package com.chefcontrol.api.user.dto;

import com.chefcontrol.domain.user.Permission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SetPermissionOverridesRequest(
        @NotNull @Valid List<OverrideItem> overrides
) {
    public record OverrideItem(@NotNull Permission permission, boolean granted) {}
}
