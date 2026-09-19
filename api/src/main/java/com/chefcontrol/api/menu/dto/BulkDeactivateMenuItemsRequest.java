package com.chefcontrol.api.menu.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record BulkDeactivateMenuItemsRequest(
        @NotEmpty List<UUID> ids
) {}
