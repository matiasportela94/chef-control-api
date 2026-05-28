package com.chefcontrol.api.sale.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateSaleRequest(
        String notes,
        Instant soldAt,
        @NotEmpty @Valid List<SaleItemRequest> items
) {
    public record SaleItemRequest(
            @NotNull UUID menuItemId,
            @Min(1) int quantity
    ) {}
}
