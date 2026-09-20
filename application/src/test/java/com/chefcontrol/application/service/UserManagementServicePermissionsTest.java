package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.PasswordEncoderPort;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.repository.*;
import com.chefcontrol.domain.user.Permission;
import com.chefcontrol.domain.user.Role;
import com.chefcontrol.domain.user.UserRestaurant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ponytail: la autorización de "quién puede llamar a esto" ya la resuelve @PreAuthorize
 * en el controller (PERM_ROLES_UPDATE) — no se reimplementa acá. Lo único que este service
 * sigue garantizando por sí mismo es la regla de negocio real: nunca tocar overrides ni
 * reasignar el rol de sistema (SUPERADMIN), sea quien sea el que llame.
 */
@ExtendWith(MockitoExtension.class)
class UserManagementServicePermissionsTest {

    @Mock UserRepository userRepository;
    @Mock UserRestaurantRepository userRestaurantRepository;
    @Mock RoleRepository roleRepository;
    @Mock RestaurantRepository restaurantRepository;
    @Mock PermissionOverrideRepository permissionOverrideRepository;
    @Mock PermissionResolutionService permissionResolutionService;
    @Mock PasswordEncoderPort passwordEncoder;
    @Mock PasswordResetService passwordResetService;
    @Mock AuditService auditService;

    private final UUID restaurantId = UUID.randomUUID();
    private final UUID targetUserId = UUID.randomUUID();
    private final UUID roleId = UUID.randomUUID();

    private UserManagementService service() {
        return new UserManagementService(userRepository, userRestaurantRepository, roleRepository,
                restaurantRepository, permissionOverrideRepository, permissionResolutionService,
                passwordEncoder, passwordResetService, auditService);
    }

    @BeforeEach
    void setTenant() {
        TenantContext.set(restaurantId);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private UserRestaurant membership(boolean roleIsSystem) {
        return UserRestaurant.builder()
                .userId(targetUserId).restaurantId(restaurantId)
                .roleId(roleId).roleName(roleIsSystem ? "SUPERADMIN" : "KITCHEN")
                .roleIsSystem(roleIsSystem).isActive(true).build();
    }

    @Test
    void cannotSetOverridesForTheAccountOwner() {
        when(userRestaurantRepository.findByUserIdAndRestaurantId(targetUserId, restaurantId))
                .thenReturn(Optional.of(membership(true)));

        var overrides = List.of(new UserManagementService.PermissionOverrideCommand(Permission.FOOD_COST_VIEW, true));

        assertThatThrownBy(() -> service().setPermissionOverrides(targetUserId, overrides))
                .isInstanceOf(AppException.class);

        verify(permissionOverrideRepository, never()).replaceAll(any(), any(), any());
    }

    @Test
    void setsOverridesForARegularUser() {
        when(userRestaurantRepository.findByUserIdAndRestaurantId(targetUserId, restaurantId))
                .thenReturn(Optional.of(membership(false)));

        var overrides = List.of(new UserManagementService.PermissionOverrideCommand(Permission.FOOD_COST_VIEW, true));

        service().setPermissionOverrides(targetUserId, overrides);

        verify(permissionOverrideRepository).replaceAll(eq(targetUserId), eq(restaurantId), any());
        verify(auditService).log(eq(AuditAction.USER_PERMISSIONS_UPDATED), eq("User"), eq(targetUserId), any());
    }

    @Test
    void cannotAssignTheSystemRoleToAUserViaUpdate() {
        UUID accountId = UUID.randomUUID();
        var restaurant = new com.chefcontrol.domain.restaurant.Restaurant();
        restaurant.setId(restaurantId);
        restaurant.setAccountId(accountId);

        when(userRestaurantRepository.findByUserIdAndRestaurantId(targetUserId, restaurantId))
                .thenReturn(Optional.of(membership(false)));
        when(restaurantRepository.findByIdAndIsActiveTrue(restaurantId)).thenReturn(Optional.of(restaurant));
        when(roleRepository.findByIdAndAccountId(roleId, accountId))
                .thenReturn(Optional.of(Role.builder().id(roleId).accountId(accountId).name("SUPERADMIN").isSystem(true).build()));

        var cmd = new UserManagementService.UpdateUserCommand("Nuevo nombre", null, roleId);

        assertThatThrownBy(() -> service().updateUser(targetUserId, cmd))
                .isInstanceOf(AppException.class);

        verify(userRestaurantRepository, never()).save(any());
    }
}
