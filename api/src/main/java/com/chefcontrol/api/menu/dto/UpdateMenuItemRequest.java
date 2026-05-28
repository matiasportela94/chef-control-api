package com.chefcontrol.api.menu.dto;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record UpdateMenuItemRequest(
        String name,
        String description,
        @Positive BigDecimal price,
        String category
) {}
