package com.chefcontrol.api.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateProductRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 100) String sku,
        @NotNull UUID defaultUnitId,
        UUID categoryId,
        @DecimalMin("0") BigDecimal minStock,
        @DecimalMin("0") BigDecimal maxStock
) {}
