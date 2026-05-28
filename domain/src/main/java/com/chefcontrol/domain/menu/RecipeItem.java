package com.chefcontrol.domain.menu;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class RecipeItem {

    private UUID id;
    private UUID recipeId;
    private UUID productId;
    private String productName;
    private BigDecimal quantity;
    private UUID unitId;
    private String unitName;
}
