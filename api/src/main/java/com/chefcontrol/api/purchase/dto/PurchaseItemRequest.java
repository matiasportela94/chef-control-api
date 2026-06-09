package com.chefcontrol.api.purchase.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PurchaseItemRequest(
        @NotNull UUID productId,
        @NotNull UUID unitId,
        @NotNull @DecimalMin("0.001") BigDecimal quantity,
        @NotNull @DecimalMin("0") BigDecimal pricePerUnit,
        LocalDate expirationDate
) {}
