package com.chefcontrol.domain.menu;

import lombok.*;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Una selección de platos del catálogo, con nombre propio. Un restaurante puede tener varias
 * activas a la vez (almuerzo y cena, salón y delivery) y un mismo plato puede estar en varias.
 *
 * La carta NO tiene precio propio: el precio vive en {@link MenuItem} y es el mismo esté en la
 * carta que esté. Por eso las ventas no necesitan saber de qué carta sale un plato.
 *
 * Tampoco se versiona: agregar o sacar platos modifica la carta y punto. El "cuándo" y el "quién"
 * salen de audit_log (CARTA_ITEMS_ADDED / CARTA_ITEMS_REMOVED).
 */
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class Carta {

    private UUID id;
    private UUID restaurantId;
    private String name;
    private boolean active = true;
    private Instant createdAt;

    @Builder.Default
    private Set<UUID> menuItemIds = new LinkedHashSet<>();

    /** Idempotente: devuelve false si el plato ya estaba en la carta. */
    public boolean addItem(UUID menuItemId) {
        return menuItemIds.add(menuItemId);
    }

    /** Idempotente: devuelve false si el plato no estaba en la carta. */
    public boolean removeItem(UUID menuItemId) {
        return menuItemIds.remove(menuItemId);
    }

    public boolean contains(UUID menuItemId) {
        return menuItemIds.contains(menuItemId);
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public int itemCount() {
        return menuItemIds.size();
    }
}
