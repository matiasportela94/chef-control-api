package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.history.RecipeVersion;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "recipe_versions")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class RecipeVersionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "menu_item_id", nullable = false)
    private UUID menuItemId;

    @Column(nullable = false)
    private int servings;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "changed_by")
    private UUID changedBy;

    @OneToMany(mappedBy = "recipeVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RecipeVersionItemJpaEntity> items = new ArrayList<>();

    public static RecipeVersionJpaEntity from(RecipeVersion domain) {
        RecipeVersionJpaEntity e = RecipeVersionJpaEntity.builder()
                .id(domain.id())
                .restaurantId(domain.restaurantId())
                .menuItemId(domain.menuItemId())
                .servings(domain.servings())
                .validFrom(domain.validFrom())
                .changedBy(domain.changedBy())
                .items(new ArrayList<>())
                .build();
        domain.items().forEach(item -> {
            RecipeVersionItemJpaEntity child = RecipeVersionItemJpaEntity.builder()
                    .productId(item.productId())
                    .quantity(item.quantity())
                    .unitId(item.unitId())
                    .recipeVersion(e)
                    .build();
            e.getItems().add(child);
        });
        return e;
    }

    public RecipeVersion toDomain() {
        return new RecipeVersion(id, restaurantId, menuItemId, servings, validFrom, changedBy,
                items.stream()
                        .map(i -> new RecipeVersion.Item(i.getProductId(), i.getQuantity(), i.getUnitId()))
                        .toList());
    }
}
