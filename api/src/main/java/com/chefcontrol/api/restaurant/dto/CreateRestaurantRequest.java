package com.chefcontrol.api.restaurant.dto;

import com.chefcontrol.domain.restaurant.TaxCondition;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * También el PUT. {@code taxCondition} null = no cargada (al crear) o sin cambios (al editar).
 * {@code salesIvaPercentage} null = 21 (al crear) o sin cambios (al editar).
 */
public record CreateRestaurantRequest(
        @NotBlank String name,
        String timezone,
        TaxCondition taxCondition,
        BigDecimal salesIvaPercentage
) {}
