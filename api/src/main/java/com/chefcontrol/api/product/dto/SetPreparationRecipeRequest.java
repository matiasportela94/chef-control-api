package com.chefcontrol.api.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** {@code yieldQuantity}: cuánto rinde una tanda, en la unidad por defecto del insumo. */
public record SetPreparationRecipeRequest(
        @NotNull @Positive BigDecimal yieldQuantity,
        @NotEmpty @Size(max = 200) List<@Valid @NotNull Item> items
) {
    public record Item(@NotNull UUID productId, @NotNull @Positive BigDecimal quantity, @NotNull UUID unitId) {}
}
