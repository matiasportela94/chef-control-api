package com.chefcontrol.api.restaurant.dto;

import com.chefcontrol.domain.restaurant.TaxCondition;
import jakarta.validation.constraints.NotBlank;

/** También el PUT. {@code taxCondition} null = no cargada (al crear) o sin cambios (al editar). */
public record CreateRestaurantRequest(
        @NotBlank String name,
        String timezone,
        TaxCondition taxCondition
) {}
