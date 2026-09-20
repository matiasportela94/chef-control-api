package com.chefcontrol.application.service;

import com.chefcontrol.domain.repository.PermissionOverrideRepository;
import com.chefcontrol.domain.repository.RoleRepository;
import com.chefcontrol.domain.user.Permission;
import com.chefcontrol.domain.user.PermissionOverride;
import com.chefcontrol.domain.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Único lugar que sabe combinar los permisos del rol (persistido, editable) con los overrides
 * puntuales del usuario. Lo usa tanto JwtTokenProvider (para el claim del token) como
 * UserManagementService (para mostrar/editar el set en /users) — nunca reimplementar el merge
 * en otro lado. Si el rol es de sistema (SUPERADMIN), tiene todo siempre — ver {@link Role#effectivePermissions()}.
 */
@Service
@RequiredArgsConstructor
public class PermissionResolutionService {

    private final RoleRepository roleRepository;
    private final PermissionOverrideRepository permissionOverrideRepository;

    public Set<Permission> resolveEffectivePermissions(UUID userId, UUID restaurantId, UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalStateException("Role not found: " + roleId));

        Set<Permission> effective = new HashSet<>(role.effectivePermissions());
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
