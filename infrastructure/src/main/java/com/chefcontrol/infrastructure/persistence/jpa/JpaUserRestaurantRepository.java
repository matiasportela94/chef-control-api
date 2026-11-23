package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.UserRestaurantJpaEntity;
import com.chefcontrol.infrastructure.persistence.entity.UserRestaurantJpaId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    boolean existsByRole_IdAndIsActiveTrue(UUID roleId);

    // ponytail: compara el texto tal cual ("+54 9 11…" y "11…" son distintos); WhatsApp (F20) va a necesitar E.164.
    @Query("SELECT CASE WHEN COUNT(ur) > 0 THEN true ELSE false END FROM UserRestaurantJpaEntity ur JOIN ur.user u " +
           "WHERE ur.id.restaurantId = :restaurantId AND ur.isActive = true AND u.id <> :excludingUserId " +
           "AND COALESCE(ur.phone, u.phone) = :phone")
    boolean phoneTakenInRestaurant(@Param("restaurantId") UUID restaurantId, @Param("phone") String phone,
                                   @Param("excludingUserId") UUID excludingUserId);

    @Query("SELECT CASE WHEN COUNT(ur) > 0 THEN true ELSE false END FROM UserRestaurantJpaEntity ur " +
           "WHERE ur.user.id = :userId AND ur.isActive = true " +
           "AND ur.restaurant.isActive = true AND ur.restaurant.accountId <> :accountId")
    boolean existsActiveMembershipInOtherAccount(
            @Param("userId") UUID userId,
            @Param("accountId") UUID accountId);

    @Query("SELECT DISTINCT ur.user.id FROM UserRestaurantJpaEntity ur " +
           "WHERE ur.user.id IN :userIds AND ur.isActive = true " +
           "AND ur.restaurant.isActive = true AND ur.restaurant.accountId <> :accountId")
    Set<UUID> findUserIdsWithActiveMembershipInOtherAccount(
            @Param("userIds") Collection<UUID> userIds,
            @Param("accountId") UUID accountId);
}
