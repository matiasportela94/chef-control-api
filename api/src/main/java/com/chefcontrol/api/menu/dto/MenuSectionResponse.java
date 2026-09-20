package com.chefcontrol.api.menu.dto;

import com.chefcontrol.domain.menu.MenuSection;

import java.util.UUID;

public record MenuSectionResponse(
        UUID id,
        String name,
        int sortOrder,
        String color
) {
    public static MenuSectionResponse from(MenuSection section) {
        return new MenuSectionResponse(
                section.getId(),
                section.getName(),
                section.getSortOrder(),
                section.getColor());
    }
}
