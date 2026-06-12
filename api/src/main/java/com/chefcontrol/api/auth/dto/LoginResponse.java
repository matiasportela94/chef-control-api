package com.chefcontrol.api.auth.dto;

import java.util.List;
import java.util.UUID;

public record LoginResponse(
        UUID userId,
        String name,
        String email,
        UUID activeRestaurantId,
        String activeRestaurantName,
        String role,
        long expiresAt,
        List<RestaurantSummary> restaurants
) {
    public record RestaurantSummary(UUID id, String name, String role) {}
}
