package com.chefcontrol.api.menu.dto;

import com.chefcontrol.domain.menu.MenuItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MenuItemResponse(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        String category,
        boolean active,
        Instant createdAt
) {
    public static MenuItemResponse from(MenuItem item) {
        return new MenuItemResponse(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getPrice(),
                item.getCategory(),
                item.isActive(),
                item.getCreatedAt()
        );
    }
}
