package com.chefcontrol.api.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateMenuItemRequest(
        @NotBlank String name,
        String description,
        @Positive BigDecimal price,
        UUID sectionId
) {}
