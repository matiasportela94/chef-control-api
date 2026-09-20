package com.chefcontrol.api.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMenuSectionRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 7) String color,
        @Size(max = 50) String icon
) {}
