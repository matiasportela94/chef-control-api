package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.user.PermissionOverride;

import java.util.List;
import java.util.UUID;

public interface PermissionOverrideRepository {

    List<PermissionOverride> findByUserIdAndRestaurantId(UUID userId, UUID restaurantId);

    /** Reemplaza todo el set de overrides de un usuario en un restaurante (borra + inserta). */
    void replaceAll(UUID userId, UUID restaurantId, List<PermissionOverride> overrides);
}
