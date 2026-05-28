package com.chefcontrol.api.auth.dto;

import java.util.List;
import java.util.UUID;

public record LoginResponse(
        String token,
        UUID userId,
        String name,
        String email,
        UUID activeRestaurantId,
        String activeRestaurantName,
        String role,
        List<RestaurantSummary> restaurants
) {
    public record RestaurantSummary(UUID id, String name, String role) {}
}
