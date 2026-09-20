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
        SectionSummary section,
        boolean active,
        Instant createdAt
) {
    public static MenuItemResponse from(MenuItem item) {
        return new MenuItemResponse(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getPrice(),
                SectionSummary.from(item),
                item.isActive(),
                item.getCreatedAt()
        );
    }

    /** El color viaja con el plato: la tarjeta de /menu se pinta con el color de su paso. */
    public record SectionSummary(UUID id, String name, String color, Integer sortOrder) {
        static SectionSummary from(MenuItem item) {
            if (item.getSectionId() == null) return null;
            return new SectionSummary(
                    item.getSectionId(),
                    item.getSectionName(),
                    item.getSectionColor(),
                    item.getSectionSortOrder());
        }
    }
}
