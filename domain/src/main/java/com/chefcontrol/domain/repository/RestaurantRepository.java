package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.restaurant.Restaurant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantRepository {

    Optional<Restaurant> findByIdAndIsActiveTrue(UUID id);

    /** Incluye inactivos — la pantalla de administración necesita verlos para reactivarlos. */
    Optional<Restaurant> findById(UUID id);

    boolean existsBySlug(String slug);

    Restaurant save(Restaurant restaurant);

    List<Restaurant> findAllByAccountId(UUID accountId);

    /** Borrado duro: arrastra toda la data del restaurante vía ON DELETE CASCADE (ver V15). */
    void deleteById(UUID id);
}
