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
        @NotEmpty @Size(max = 200) List<@Valid @NotNull PreparationRecipeItemRequest> items
) {
    /** Nombre único: es un schema del spec (un "Item" pelado chocaba con el de la respuesta). */
    public record PreparationRecipeItemRequest(@NotNull UUID productId, @NotNull @Positive BigDecimal quantity, @NotNull UUID unitId) {}
}
