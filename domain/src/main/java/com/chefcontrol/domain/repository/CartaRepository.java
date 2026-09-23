package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.menu.Carta;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartaRepository {

    List<Carta> findAllByRestaurantId(UUID restaurantId);

    Optional<Carta> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    boolean existsByRestaurantIdAndNameIgnoreCase(UUID restaurantId, String name);

    Carta save(Carta carta);

    /**
     * Saca un plato de todas las cartas del restaurante. Se llama al darlo de baja del catálogo:
     * una carta no puede seguir ofreciendo algo que ya no existe.
     */
    void removeMenuItemFromAllCartas(UUID menuItemId);

    void delete(UUID id);
}
