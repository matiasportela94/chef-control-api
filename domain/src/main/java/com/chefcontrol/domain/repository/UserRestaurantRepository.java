package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.user.UserRestaurant;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UserRestaurantRepository {

    List<UserRestaurant> findActiveByUserId(UUID userId);

    Optional<UserRestaurant> findActiveByUserIdAndRestaurantId(UUID userId, UUID restaurantId);

    List<UserRestaurant> findActiveByRestaurantId(UUID restaurantId);

    Optional<UserRestaurant> findByUserIdAndRestaurantId(UUID userId, UUID restaurantId);

    UserRestaurant save(UserRestaurant membership);

    /**
     * Si otra persona con membresía activa en ese local tiene ese teléfono como efectivo (el de su membresía o, si no
     * tiene, el personal). La única regla de unicidad del teléfono: entre locales se repite libremente.
     */
    boolean phoneTakenInRestaurant(UUID restaurantId, String phone, UUID excludingUserId);

    /** Para bloquear el borrado de un rol que todavía tiene gente asignada. */
    boolean existsByRoleIdAndIsActiveTrue(UUID roleId);

    /** Si tiene membresía activa en un local activo de otra cuenta, su perfil (nombre y teléfono) no se edita desde /users. */
    boolean existsActiveMembershipInOtherAccount(UUID userId, UUID accountId);

    Set<UUID> findUserIdsWithActiveMembershipInOtherAccount(Collection<UUID> userIds, UUID accountId);
}
