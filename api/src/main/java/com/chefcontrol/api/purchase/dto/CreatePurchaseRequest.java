package com.chefcontrol.api.purchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreatePurchaseRequest(
        UUID supplierId,
        @Size(max = 1000) String notes,
        Instant purchasedAt,
        @NotEmpty @Valid List<PurchaseItemRequest> items
) {}
