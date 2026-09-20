package com.chefcontrol.api.carta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateCartaRequest(
        @NotBlank @Size(max = 255) String name,
        List<UUID> menuItemIds
) {
    /** Una carta puede nacer vacía y llenarse después. */
    public List<UUID> menuItemIds() {
        return menuItemIds == null ? List.of() : menuItemIds;
    }
}
