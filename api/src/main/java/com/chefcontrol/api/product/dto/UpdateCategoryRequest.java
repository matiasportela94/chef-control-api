package com.chefcontrol.api.product.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
        @Size(max = 100) String name,
        @Size(max = 255) String description,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be a valid hex code (#RRGGBB)")
        String color,
        @Size(max = 64) String icon
) {}
