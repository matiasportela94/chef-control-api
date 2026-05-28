package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.menu.Recipe;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "recipes")
@Getter @Setter @NoArgsConstructor
public class RecipeJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "menu_item_id", nullable = false)
    private UUID menuItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_item_id", insertable = false, updatable = false)
    private MenuItemJpaEntity menuItem;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(nullable = false)
    private int servings = 1;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<RecipeItemJpaEntity> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public static RecipeJpaEntity from(Recipe domain) {
        RecipeJpaEntity e = new RecipeJpaEntity();
        e.setId(domain.getId());
        e.setMenuItemId(domain.getMenuItemId());
        e.setRestaurantId(domain.getRestaurantId());
        e.setServings(domain.getServings());
        e.setCreatedAt(domain.getCreatedAt());
        if (domain.getItems() != null) {
            List<RecipeItemJpaEntity> itemEntities = domain.getItems().stream()
                    .map(item -> RecipeItemJpaEntity.from(item, e))
                    .collect(Collectors.toList());
            e.setItems(itemEntities);
        }
        return e;
    }

    public Recipe toDomain() {
        return Recipe.builder()
                .id(id)
                .menuItemId(menuItemId)
                .restaurantId(restaurantId)
                .servings(servings)
                .createdAt(createdAt)
                .items(items == null ? new ArrayList<>() :
                       items.stream().map(RecipeItemJpaEntity::toDomain).collect(Collectors.toList()))
                .build();
    }
}
