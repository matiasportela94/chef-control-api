package com.chefcontrol.application.service;

import com.chefcontrol.domain.repository.PermissionOverrideRepository;
import com.chefcontrol.domain.user.Permission;
import com.chefcontrol.domain.user.PermissionOverride;
import com.chefcontrol.domain.user.RoleName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * ponytail: la única lógica real acá es el merge default±overrides — todo lo demás
 * (el catálogo, la matriz por rol) es data declarativa que no necesita test unitario.
 */
@ExtendWith(MockitoExtension.class)
class PermissionResolutionServiceTest {

    @Mock PermissionOverrideRepository permissionOverrideRepository;

    private final UUID userId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();

    private PermissionResolutionService service() {
        return new PermissionResolutionService(permissionOverrideRepository);
    }

    @Test
    void noOverrides_returnsExactlyRoleDefaults() {
        when(permissionOverrideRepository.findByUserIdAndRestaurantId(userId, restaurantId))
                .thenReturn(List.of());

        var effective = service().resolveEffectivePermissions(userId, restaurantId, RoleName.READONLY);

        assertThat(effective).isEqualTo(RoleName.READONLY.defaultPermissions());
        assertThat(effective).noneMatch(p -> p.name().endsWith("_MANAGE"));
    }

    @Test
    void grantOverride_addsPermissionKitchenDoesNotHaveByDefault() {
        when(permissionOverrideRepository.findByUserIdAndRestaurantId(userId, restaurantId))
                .thenReturn(List.of(override(Permission.FOOD_COST_VIEW, true)));

        var effective = service().resolveEffectivePermissions(userId, restaurantId, RoleName.KITCHEN);

        assertThat(effective).contains(Permission.FOOD_COST_VIEW);
        assertThat(RoleName.KITCHEN.defaultPermissions()).doesNotContain(Permission.FOOD_COST_VIEW);
    }

    @Test
    void revokeOverride_removesPermissionManagerHasByDefault() {
        when(permissionOverrideRepository.findByUserIdAndRestaurantId(userId, restaurantId))
                .thenReturn(List.of(override(Permission.USERS_MANAGE, false)));

        var effective = service().resolveEffectivePermissions(userId, restaurantId, RoleName.MANAGER);

        assertThat(effective).doesNotContain(Permission.USERS_MANAGE);
        assertThat(effective).contains(Permission.PRODUCTS_MANAGE); // el resto del default de MANAGER sigue intacto
    }

    private PermissionOverride override(Permission permission, boolean granted) {
        return PermissionOverride.builder()
                .userId(userId).restaurantId(restaurantId)
                .permission(permission).granted(granted)
                .build();
    }
}
