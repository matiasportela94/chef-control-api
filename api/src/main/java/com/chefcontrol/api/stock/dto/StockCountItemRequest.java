package com.chefcontrol.api.stock.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record StockCountItemRequest(
        @NotNull UUID productId,
        @NotNull UUID unitId,
        @NotNull @DecimalMin("0.0") BigDecimal countedQuantity
) {}
