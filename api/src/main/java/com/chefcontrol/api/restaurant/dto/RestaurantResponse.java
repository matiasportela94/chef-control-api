package com.chefcontrol.api.restaurant.dto;

import com.chefcontrol.domain.restaurant.Restaurant;

import java.time.Instant;
import java.util.UUID;

public record RestaurantResponse(
        UUID id,
        String name,
        String slug,
        String timezone,
        boolean isActive,
        Instant createdAt
) {
    public static RestaurantResponse from(Restaurant r) {
        return new RestaurantResponse(r.getId(), r.getName(), r.getSlug(), r.getTimezone(), r.isActive(), r.getCreatedAt());
    }
}
