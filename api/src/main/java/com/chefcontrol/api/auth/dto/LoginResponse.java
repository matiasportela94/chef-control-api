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
        List<String> permissions,
        // Features del plan efectivo de la cuenta del local activo: el front las usa para no ofrecer lo que el
        // backend va a rechazar. Pueden quedar viejas hasta renovar la sesión; quien decide es el backend.
        List<String> features,
        long expiresAt,
        List<RestaurantSummary> restaurants
) {
    /**
     * {@code accountName} está para que el switcher pueda agrupar por cuenta. Sin eso, a quien
     * lo invitan al restaurante de otro le aparecen locales ajenos mezclados con los propios y
     * no hay forma de distinguirlos.
     */
    public record RestaurantSummary(UUID id, String name, String role,
                                    UUID accountId, String accountName) {}
}
