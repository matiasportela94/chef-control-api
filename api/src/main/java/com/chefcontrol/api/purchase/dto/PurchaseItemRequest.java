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
        LocalDate expirationDate,
        /** Null = la del insumo, y si no tiene, 0. Un 0 explícito es "sin IVA". Fuera de 0, 2.5, 5, 10.5, 21, 27
         *  → INVALID_IVA_PERCENTAGE. */
        BigDecimal ivaPercentage
) {}
