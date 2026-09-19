package com.chefcontrol.application.service;

import com.chefcontrol.domain.repository.PermissionOverrideRepository;
import com.chefcontrol.domain.user.Permission;
import com.chefcontrol.domain.user.PermissionOverride;
import com.chefcontrol.domain.user.RoleName;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Único lugar que sabe combinar el default del rol con los overrides puntuales del usuario.
 * Lo usa tanto JwtTokenProvider (para el claim del token) como UserManagementService
 * (para mostrar el set efectivo en la pantalla de edición) — nunca reimplementar el merge
 * en otro lado.
 */
@Service
@RequiredArgsConstructor
public class PermissionResolutionService {

    private final PermissionOverrideRepository permissionOverrideRepository;

    public Set<Permission> resolveEffectivePermissions(UUID userId, UUID restaurantId, RoleName role) {
        Set<Permission> effective = new HashSet<>(role.defaultPermissions());
        for (PermissionOverride override : permissionOverrideRepository.findByUserIdAndRestaurantId(userId, restaurantId)) {
            if (override.isGranted()) {
                effective.add(override.getPermission());
            } else {
                effective.remove(override.getPermission());
            }
        }
        return effective;
    }
}
