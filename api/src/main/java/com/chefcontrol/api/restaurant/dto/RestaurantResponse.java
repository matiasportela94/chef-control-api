package com.chefcontrol.api.restaurant.dto;

import com.chefcontrol.domain.restaurant.Restaurant;
import com.chefcontrol.domain.restaurant.TaxCondition;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RestaurantResponse(
        UUID id,
        String name,
        String slug,
        String timezone,
        boolean isActive,
        /** Null = no cargada: las compras se toman como de un monotributista. */
        TaxCondition taxCondition,
        /** La alícuota con la que vende; solo cuenta si es RI (separa el IVA del ingreso para el food cost). */
        BigDecimal salesIvaPercentage,
        Instant createdAt
) {
    public static RestaurantResponse from(Restaurant r) {
        return new RestaurantResponse(r.getId(), r.getName(), r.getSlug(), r.getTimezone(), r.isActive(),
                r.getTaxCondition(), r.getSalesIvaPercentage(), r.getCreatedAt());
    }
}
