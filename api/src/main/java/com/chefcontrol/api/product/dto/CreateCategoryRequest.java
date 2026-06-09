package com.chefcontrol.api.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 100) String name,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be a valid hex code (#RRGGBB)")
        String color,
        UUID parentId
) {}
