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
        Instant imageUpdatedAt,
        Instant createdAt
) {
    public static MenuItemResponse from(MenuItem item) {
        return from(item, null);
    }

    /** {@code imageUpdatedAt} se mergea en el borde: el dominio del plato no sabe de fotos. */
    public static MenuItemResponse from(MenuItem item, Instant imageUpdatedAt) {
        return new MenuItemResponse(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getPrice(),
                SectionSummary.from(item),
                item.isActive(),
                imageUpdatedAt,
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
