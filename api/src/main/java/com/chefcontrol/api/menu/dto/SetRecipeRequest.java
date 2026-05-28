package com.chefcontrol.api.menu.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SetRecipeRequest(
        @Min(1) int servings,
        @NotEmpty @Valid List<RecipeItemRequest> items
) {
    public record RecipeItemRequest(
            @NotNull UUID productId,
            @NotNull UUID unitId,
            @NotNull @Positive BigDecimal quantity
    ) {}
}
