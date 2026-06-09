package com.chefcontrol.api.product.dto;

import com.chefcontrol.domain.product.ProductCategory;

import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String color,
        boolean isSystem,
        UUID parentId
) {
    public static CategoryResponse from(ProductCategory category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getColor(),
                category.isSystem(),
                category.getParentId());
    }
}
