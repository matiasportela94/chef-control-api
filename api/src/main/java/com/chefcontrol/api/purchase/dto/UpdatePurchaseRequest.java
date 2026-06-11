package com.chefcontrol.api.purchase.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UpdatePurchaseRequest(
        UUID supplierId,
        @Size(max = 1000) String notes,
        Instant purchasedAt,
        List<ItemPriceUpdate> items
) {
    public record ItemPriceUpdate(
            @NotNull UUID id,
            @NotNull @DecimalMin(value = "0.01") BigDecimal pricePerUnit
    ) {}
}
