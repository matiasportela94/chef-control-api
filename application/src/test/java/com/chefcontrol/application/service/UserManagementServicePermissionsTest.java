package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.application.port.PasswordEncoderPort;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.repository.*;
import com.chefcontrol.domain.user.Permission;
import com.chefcontrol.domain.user.RoleName;
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
 * ponytail: solo cubre la regla que importa de verdad acá — que un MANAGER no pueda
 * tocar overrides de permisos de nadie (agujero de escalamiento si se permitiera).
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
    @Mock CurrentUserProvider currentUserProvider;

    private final UUID restaurantId = UUID.randomUUID();
    private final UUID targetUserId = UUID.randomUUID();

    private UserManagementService service() {
        return new UserManagementService(userRepository, userRestaurantRepository, roleRepository,
                restaurantRepository, permissionOverrideRepository, permissionResolutionService,
                passwordEncoder, passwordResetService, auditService, currentUserProvider);
    }

    @BeforeEach
    void setTenant() {
        TenantContext.set(restaurantId);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void managerCannotEditPermissionOverrides() {
        when(currentUserProvider.currentRole()).thenReturn("MANAGER");

        var overrides = List.of(new UserManagementService.PermissionOverrideCommand(Permission.USERS_MANAGE, true));

        assertThatThrownBy(() -> service().setPermissionOverrides(targetUserId, overrides))
                .isInstanceOf(AppException.class);

        verify(permissionOverrideRepository, never()).replaceAll(any(), any(), any());
        verify(auditService, never()).log(any(), any(), any(), any());
    }

    @Test
    void ownerCanEditPermissionOverrides() {
        when(currentUserProvider.currentRole()).thenReturn("OWNER");
        UserRestaurant membership = UserRestaurant.builder()
                .userId(targetUserId).restaurantId(restaurantId).roleName(RoleName.KITCHEN).isActive(true).build();
        when(userRestaurantRepository.findByUserIdAndRestaurantId(targetUserId, restaurantId))
                .thenReturn(Optional.of(membership));

        var overrides = List.of(new UserManagementService.PermissionOverrideCommand(Permission.FOOD_COST_VIEW, true));

        service().setPermissionOverrides(targetUserId, overrides);

        verify(permissionOverrideRepository).replaceAll(eq(targetUserId), eq(restaurantId), any());
        verify(auditService).log(eq(AuditAction.USER_PERMISSIONS_UPDATED), eq("User"), eq(targetUserId), any());
    }
}
