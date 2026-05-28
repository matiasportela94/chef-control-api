package com.chefcontrol.domain.menu;

import java.math.BigDecimal;
import java.util.UUID;

public record RecipeIngredient(UUID productId, BigDecimal quantity, UUID unitId) {}
