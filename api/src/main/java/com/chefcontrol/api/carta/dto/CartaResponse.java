package com.chefcontrol.api.carta.dto;

import com.chefcontrol.domain.menu.Carta;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CartaResponse(
        UUID id,
        String name,
        boolean active,
        int itemCount,
        List<UUID> menuItemIds,
        Instant createdAt
) {
    public static CartaResponse from(Carta carta) {
        return new CartaResponse(
                carta.getId(),
                carta.getName(),
                carta.isActive(),
                carta.itemCount(),
                List.copyOf(carta.getMenuItemIds()),
                carta.getCreatedAt()
        );
    }
}
