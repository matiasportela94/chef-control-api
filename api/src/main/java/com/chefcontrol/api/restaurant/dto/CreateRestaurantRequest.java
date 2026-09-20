package com.chefcontrol.api.restaurant.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRestaurantRequest(
        @NotBlank String name,
        String timezone
) {}
