package com.chefcontrol.application.service;

import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.PasswordEncoderPort;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.domain.repository.PermissionOverrideRepository;
import com.chefcontrol.domain.repository.RestaurantRepository;
import com.chefcontrol.domain.repository.RoleRepository;
import com.chefcontrol.domain.repository.UserRepository;
import com.chefcontrol.domain.repository.UserRestaurantRepository;
import com.chefcontrol.domain.user.Permission;
import com.chefcontrol.domain.user.PermissionOverride;
import com.chefcontrol.domain.user.Role;
import com.chefcontrol.domain.user.User;
import com.chefcontrol.domain.user.UserRestaurant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository userRepository;
    private final UserRestaurantRepository userRestaurantRepository;
    private final RoleRepository roleRepository;
    private final RestaurantRepository restaurantRepository;
    private final PermissionOverrideRepository permissionOverrideRepository;
    private final PermissionResolutionService permissionResolutionService;
    private final PasswordEncoderPort passwordEncoder;
    private final PasswordResetService passwordResetService;
    private final AuditService auditService;

    public List<UserRestaurant> listUsers() {
        return userRestaurantRepository.findActiveByRestaurantId(TenantContext.require());
    }

    public UserRestaurant getUser(UUID userId) {
        return userRestaurantRepository.findByUserIdAndRestaurantId(userId, TenantContext.require())
                .orElseThrow(() -> AppException.notFound(ErrorCode.USER_NOT_FOUND, "User not found in this restaurant"));
    }

    @Transactional
    public CreatedUser createUser(CreateUserCommand cmd) {
        UUID restaurantId = TenantContext.require();

        if (userRepository.existsByEmail(cmd.email())) {
            throw AppException.conflict(ErrorCode.DUPLICATE_EMAIL, "Email already in use");
        }
        if (cmd.phone() != null && userRepository.existsByPhone(cmd.phone())) {
            throw AppException.conflict(ErrorCode.DUPLICATE_PHONE, "Phone number already registered");
        }

        var restaurant = restaurantRepository.findByIdAndIsActiveTrue(restaurantId)
                .orElseThrow(() -> new IllegalStateException("Restaurant not found"));
        Role role = resolveAssignableRole(cmd.roleId(), restaurant.getAccountId());

        User user = new User();
        user.setEmail(cmd.email());
        user.setName(cmd.name());
        user.setPhone(cmd.phone());
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user = userRepository.save(user);

        UserRestaurant membership = UserRestaurant.builder()
                .userId(user.getId())
                .restaurantId(restaurantId)
                .roleId(role.getId())
                .roleName(role.getName())
                .isActive(true)
                .build();
        membership = userRestaurantRepository.save(membership);

        try {
            passwordResetService.createAndSendSetPasswordToken(user, restaurant);
        } catch (Exception e) {
            // Notification failure must not block user creation
        }

        auditService.log(AuditAction.USER_CREATED, "User", user.getId(),
                Map.of("email", cmd.email(), "role", role.getName(), "restaurantId", restaurantId));

        return new CreatedUser(membership);
    }

    @Transactional
    public UserRestaurant updateUser(UUID userId, UpdateUserCommand cmd) {
        UUID restaurantId = TenantContext.require();

        UserRestaurant membership = userRestaurantRepository.findByUserIdAndRestaurantId(userId, restaurantId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.USER_NOT_FOUND, "User not found in this restaurant"));

        var restaurant = restaurantRepository.findByIdAndIsActiveTrue(restaurantId)
                .orElseThrow(() -> new IllegalStateException("Restaurant not found"));
        Role role = resolveAssignableRole(cmd.roleId(), restaurant.getAccountId());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.USER_NOT_FOUND, "User not found"));

        user.setName(cmd.name());

        if (cmd.phone() != null && !cmd.phone().equals(user.getPhone())) {
            if (userRepository.existsByPhone(cmd.phone())) {
                throw AppException.conflict(ErrorCode.DUPLICATE_PHONE, "Phone number already registered");
            }
            user.setPhone(cmd.phone());
        } else if (cmd.phone() == null) {
            user.setPhone(null);
        }

        userRepository.save(user);

        membership.setRoleId(role.getId());
        membership.setRoleName(role.getName());
        membership = userRestaurantRepository.save(membership);

        auditService.log(AuditAction.USER_UPDATED, "User", userId,
                Map.of("role", role.getName(), "restaurantId", restaurantId));

        return membership;
    }

    @Transactional
    public void deactivateUser(UUID userId) {
        UUID restaurantId = TenantContext.require();

        UserRestaurant membership = userRestaurantRepository.findByUserIdAndRestaurantId(userId, restaurantId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.USER_NOT_FOUND, "User not found in this restaurant"));

        if (membership.isRoleIsSystem()) {
            throw AppException.forbidden(ErrorCode.SYSTEM_ROLE_IMMUTABLE, "Can't deactivate the account owner");
        }

        membership.deactivate();
        userRestaurantRepository.save(membership);

        auditService.log(AuditAction.USER_DEACTIVATED, "User", userId,
                Map.of("restaurantId", restaurantId));
    }

    /** Nunca se puede asignar el rol de sistema (SUPERADMIN) por esta vía — es único, del dueño de la cuenta. */
    private Role resolveAssignableRole(UUID roleId, UUID accountId) {
        Role role = roleRepository.findByIdAndAccountId(roleId, accountId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.ROLE_NOT_FOUND, "Role not found"));
        if (role.isSystem()) {
            throw AppException.forbidden(ErrorCode.SYSTEM_ROLE_IMMUTABLE, "The system role can't be assigned manually");
        }
        return role;
    }

    // ── Permisos ─────────────────────────────────────────────────────────────

    public EffectivePermissions getPermissions(UUID userId) {
        UUID restaurantId = TenantContext.require();
        UserRestaurant membership = getUser(userId); // valida ownership

        Role role = roleRepository.findById(membership.getRoleId())
                .orElseThrow(() -> new IllegalStateException("Role not found: " + membership.getRoleId()));
        List<PermissionOverride> overrides = permissionOverrideRepository
                .findByUserIdAndRestaurantId(userId, restaurantId);
        Set<Permission> effective = permissionResolutionService
                .resolveEffectivePermissions(userId, restaurantId, membership.getRoleId());

        return new EffectivePermissions(role, overrides, effective);
    }

    @Transactional
    public void setPermissionOverrides(UUID userId, List<PermissionOverrideCommand> overrides) {
        UUID restaurantId = TenantContext.require();
        UserRestaurant membership = getUser(userId); // valida que pertenezca a este restaurante

        if (membership.isRoleIsSystem()) {
            throw AppException.badRequest(ErrorCode.SYSTEM_ROLE_IMMUTABLE,
                    "The account owner already has every permission — no overrides needed or allowed");
        }

        List<PermissionOverride> domainOverrides = overrides.stream()
                .map(o -> PermissionOverride.builder()
                        .userId(userId)
                        .restaurantId(restaurantId)
                        .permission(o.permission())
                        .granted(o.granted())
                        .build())
                .toList();

        permissionOverrideRepository.replaceAll(userId, restaurantId, domainOverrides);

        auditService.log(AuditAction.USER_PERMISSIONS_UPDATED, "User", userId,
                Map.of("overrideCount", domainOverrides.size()));
    }

    // ── Commands / Results ────────────────────────────────────────────────────

    public record CreateUserCommand(
            String name,
            String email,
            String phone,
            UUID roleId
    ) {}

    public record UpdateUserCommand(
            String name,
            String phone,
            UUID roleId
    ) {}

    public record CreatedUser(UserRestaurant membership) {}

    public record PermissionOverrideCommand(Permission permission, boolean granted) {}

    public record EffectivePermissions(
            Role role,
            List<PermissionOverride> overrides,
            Set<Permission> effective
    ) {}
}
