package com.chefcontrol.api.product.dto;

import com.chefcontrol.domain.product.ProductCategory;

import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String description,
        String color,
        String icon,
        boolean isSystem,
        UUID parentId
) {
    public static CategoryResponse from(ProductCategory category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getColor(),
                category.getIcon(),
                category.isSystem(),
                category.getParentId());
    }
}
