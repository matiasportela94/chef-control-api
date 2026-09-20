package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.menu.Carta;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "cartas")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class CartaJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    /**
     * ponytail: @ElementCollection en vez de una entidad CartaItem con su adapter y su repo —
     * carta_items son dos UUIDs sin comportamiento. EAGER porque el listado de cartas muestra
     * cuántos platos tiene cada una y el frontend usa los ids para marcar los ya incluidos:
     * son decenas de filas por carta, no miles. Si una carta llegara a tener miles de platos,
     * pasar a LAZY + un count aparte.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "carta_items", joinColumns = @JoinColumn(name = "carta_id"))
    @Column(name = "menu_item_id", nullable = false)
    @Builder.Default
    private Set<UUID> menuItemIds = new LinkedHashSet<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public static CartaJpaEntity from(Carta domain) {
        return CartaJpaEntity.builder()
                .id(domain.getId())
                .restaurantId(domain.getRestaurantId())
                .name(domain.getName())
                .active(domain.isActive())
                .createdAt(domain.getCreatedAt())
                .menuItemIds(new LinkedHashSet<>(domain.getMenuItemIds()))
                .build();
    }

    public Carta toDomain() {
        return Carta.builder()
                .id(id)
                .restaurantId(restaurantId)
                .name(name)
                .active(active)
                .createdAt(createdAt)
                .menuItemIds(new LinkedHashSet<>(menuItemIds))
                .build();
    }
}
