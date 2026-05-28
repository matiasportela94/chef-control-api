package com.chefcontrol.api.product.dto;

import com.chefcontrol.domain.product.Product;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String sku,
        UnitSummary defaultUnit,
        CategorySummary category,
        BigDecimal minStock,
        BigDecimal maxStock,
        boolean isActive
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getSku(),
                UnitSummary.from(product),
                CategorySummary.from(product),
                product.getMinStock(),
                product.getMaxStock(),
                product.isActive());
    }

    public record UnitSummary(UUID id, String name, String abbreviation) {
        static UnitSummary from(Product product) {
            return new UnitSummary(
                    product.getDefaultUnitId(),
                    product.getDefaultUnitName(),
                    product.getDefaultUnitAbbreviation());
        }
    }

    public record CategorySummary(UUID id, String name, String color) {
        static CategorySummary from(Product product) {
            if (product.getCategoryId() == null) return null;
            return new CategorySummary(
                    product.getCategoryId(),
                    product.getCategoryName(),
                    product.getCategoryColor());
        }
    }
}
