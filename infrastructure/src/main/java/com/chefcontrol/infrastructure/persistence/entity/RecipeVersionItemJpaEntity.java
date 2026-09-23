package com.chefcontrol.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "recipe_version_items")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class RecipeVersionItemJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_version_id", nullable = false)
    private RecipeVersionJpaEntity recipeVersion;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;
}
