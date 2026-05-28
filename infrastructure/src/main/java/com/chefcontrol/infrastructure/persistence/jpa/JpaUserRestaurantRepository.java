package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.UserRestaurantJpaEntity;
import com.chefcontrol.infrastructure.persistence.entity.UserRestaurantJpaId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaUserRestaurantRepository extends JpaRepository<UserRestaurantJpaEntity, UserRestaurantJpaId> {

    @Query("SELECT ur FROM UserRestaurantJpaEntity ur JOIN FETCH ur.restaurant JOIN FETCH ur.role " +
           "WHERE ur.user.id = :userId AND ur.isActive = true AND ur.restaurant.isActive = true")
    List<UserRestaurantJpaEntity> findActiveByUserId(@Param("userId") UUID userId);

    @Query("SELECT ur FROM UserRestaurantJpaEntity ur JOIN FETCH ur.restaurant JOIN FETCH ur.role " +
           "WHERE ur.user.id = :userId AND ur.restaurant.id = :restaurantId AND ur.isActive = true")
    Optional<UserRestaurantJpaEntity> findActiveByUserIdAndRestaurantId(
            @Param("userId") UUID userId,
            @Param("restaurantId") UUID restaurantId);

    @Query("SELECT ur FROM UserRestaurantJpaEntity ur JOIN FETCH ur.user JOIN FETCH ur.role " +
           "WHERE ur.restaurant.id = :restaurantId AND ur.isActive = true " +
           "ORDER BY ur.user.name ASC")
    List<UserRestaurantJpaEntity> findActiveByRestaurantId(@Param("restaurantId") UUID restaurantId);

    @Query("SELECT ur FROM UserRestaurantJpaEntity ur JOIN FETCH ur.user JOIN FETCH ur.role " +
           "WHERE ur.user.id = :userId AND ur.restaurant.id = :restaurantId")
    Optional<UserRestaurantJpaEntity> findByUserIdAndRestaurantId(
            @Param("userId") UUID userId,
            @Param("restaurantId") UUID restaurantId);
}
