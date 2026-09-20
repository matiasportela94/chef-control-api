package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.restaurant.Restaurant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantRepository {

    Optional<Restaurant> findByIdAndIsActiveTrue(UUID id);

    boolean existsBySlug(String slug);

    Restaurant save(Restaurant restaurant);

    List<Restaurant> findAllByAccountId(UUID accountId);
}
