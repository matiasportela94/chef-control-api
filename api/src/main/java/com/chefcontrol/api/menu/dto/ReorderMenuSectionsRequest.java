package com.chefcontrol.api.menu.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** La posición de cada sección es su índice en esta lista. */
public record ReorderMenuSectionsRequest(
        @NotEmpty List<UUID> sectionIds
) {}
