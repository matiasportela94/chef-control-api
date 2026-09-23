package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.user.UserRestaurant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRestaurantRepository {

    List<UserRestaurant> findActiveByUserId(UUID userId);

    Optional<UserRestaurant> findActiveByUserIdAndRestaurantId(UUID userId, UUID restaurantId);

    List<UserRestaurant> findActiveByRestaurantId(UUID restaurantId);

    Optional<UserRestaurant> findByUserIdAndRestaurantId(UUID userId, UUID restaurantId);

    UserRestaurant save(UserRestaurant membership);

    /** Para bloquear el borrado de un rol que todavía tiene gente asignada. */
    boolean existsByRoleIdAndIsActiveTrue(UUID roleId);
}
