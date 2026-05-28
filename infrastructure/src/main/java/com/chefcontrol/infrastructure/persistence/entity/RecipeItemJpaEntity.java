package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.menu.RecipeItem;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "recipe_items")
@Getter @Setter @NoArgsConstructor
public class RecipeItemJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private RecipeJpaEntity recipe;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", insertable = false, updatable = false)
    private ProductJpaEntity product;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", insertable = false, updatable = false)
    private UnitJpaEntity unit;

    public static RecipeItemJpaEntity from(RecipeItem domain, RecipeJpaEntity recipeEntity) {
        RecipeItemJpaEntity e = new RecipeItemJpaEntity();
        e.setId(domain.getId());
        e.setRecipe(recipeEntity);
        e.setProductId(domain.getProductId());
        e.setQuantity(domain.getQuantity());
        e.setUnitId(domain.getUnitId());
        return e;
    }

    public RecipeItem toDomain() {
        return RecipeItem.builder()
                .id(id)
                .recipeId(recipe != null ? recipe.getId() : null)
                .productId(productId)
                .productName(product != null ? product.getName() : null)
                .quantity(quantity)
                .unitId(unitId)
                .unitName(unit != null ? unit.getName() : null)
                .build();
    }
}
