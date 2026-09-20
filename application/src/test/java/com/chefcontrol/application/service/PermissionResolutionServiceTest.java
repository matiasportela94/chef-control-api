package com.chefcontrol.application.service;

import com.chefcontrol.domain.repository.PermissionOverrideRepository;
import com.chefcontrol.domain.repository.RoleRepository;
import com.chefcontrol.domain.user.Permission;
import com.chefcontrol.domain.user.PermissionOverride;
import com.chefcontrol.domain.user.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * ponytail: la única lógica real acá es el merge permisos-del-rol ± overrides — el resto
 * (rol de sistema = todo el catálogo) es una regla de una línea en Role, no necesita más.
 */
@ExtendWith(MockitoExtension.class)
class PermissionResolutionServiceTest {

    @Mock RoleRepository roleRepository;
    @Mock PermissionOverrideRepository permissionOverrideRepository;

    private final UUID userId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();
    private final UUID roleId = UUID.randomUUID();

    private PermissionResolutionService service() {
        return new PermissionResolutionService(roleRepository, permissionOverrideRepository);
    }

    private Role role(boolean isSystem, Permission... perms) {
        return Role.builder().id(roleId).isSystem(isSystem).permissions(Set.of(perms)).build();
    }

    @Test
    void noOverrides_returnsExactlyRolePermissions() {
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role(false, Permission.PRODUCTS_VIEW)));
        when(permissionOverrideRepository.findByUserIdAndRestaurantId(userId, restaurantId)).thenReturn(List.of());

        var effective = service().resolveEffectivePermissions(userId, restaurantId, roleId);

        assertThat(effective).containsExactly(Permission.PRODUCTS_VIEW);
    }

    @Test
    void systemRole_alwaysHasFullCatalogRegardlessOfStoredPermissions() {
        // El rol de sistema no guarda filas — permissions() vacío no importa, effectivePermissions() da todo.
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role(true)));
        when(permissionOverrideRepository.findByUserIdAndRestaurantId(userId, restaurantId)).thenReturn(List.of());

        var effective = service().resolveEffectivePermissions(userId, restaurantId, roleId);

        assertThat(effective).isEqualTo(EnumSet.allOf(Permission.class));
    }

    @Test
    void grantOverride_addsPermissionTheRoleDoesNotHave() {
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role(false, Permission.WASTE_VIEW)));
        when(permissionOverrideRepository.findByUserIdAndRestaurantId(userId, restaurantId))
                .thenReturn(List.of(override(Permission.FOOD_COST_VIEW, true)));

        var effective = service().resolveEffectivePermissions(userId, restaurantId, roleId);

        assertThat(effective).contains(Permission.FOOD_COST_VIEW, Permission.WASTE_VIEW);
    }

    @Test
    void revokeOverride_removesPermissionTheRoleHasByDefault() {
        when(roleRepository.findById(roleId))
                .thenReturn(Optional.of(role(false, Permission.PRODUCTS_VIEW, Permission.PRODUCTS_DELETE)));
        when(permissionOverrideRepository.findByUserIdAndRestaurantId(userId, restaurantId))
                .thenReturn(List.of(override(Permission.PRODUCTS_DELETE, false)));

        var effective = service().resolveEffectivePermissions(userId, restaurantId, roleId);

        assertThat(effective).containsExactly(Permission.PRODUCTS_VIEW);
    }

    private PermissionOverride override(Permission permission, boolean granted) {
        return PermissionOverride.builder()
                .userId(userId).restaurantId(restaurantId)
                .permission(permission).granted(granted)
                .build();
    }
}
