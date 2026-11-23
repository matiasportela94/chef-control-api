package com.chefcontrol.application.service;

import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.EmailService;
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
import com.chefcontrol.domain.restaurant.Restaurant;
import com.chefcontrol.domain.user.Permission;
import com.chefcontrol.domain.user.PermissionOverride;
import com.chefcontrol.domain.user.Role;
import com.chefcontrol.domain.user.User;
import com.chefcontrol.domain.user.UserRestaurant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementService {

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl = "http://localhost:4200";

    private final UserRepository userRepository;
    private final UserRestaurantRepository userRestaurantRepository;
    private final RoleRepository roleRepository;
    private final RestaurantRepository restaurantRepository;
    private final PermissionOverrideRepository permissionOverrideRepository;
    private final PermissionResolutionService permissionResolutionService;
    private final PasswordEncoderPort passwordEncoder;
    private final PasswordResetService passwordResetService;
    private final EmailService emailService;
    private final AuditService auditService;

    public List<ManagedUser> listUsers() {
        UUID restaurantId = TenantContext.require();
        List<UserRestaurant> memberships = userRestaurantRepository.findActiveByRestaurantId(restaurantId);
        if (memberships.isEmpty()) {
            return List.of();
        }
        var restaurant = requireActiveRestaurant(restaurantId);
        List<UUID> userIds = memberships.stream().map(UserRestaurant::getUserId).toList();
        Set<UUID> sharedUserIds = userRestaurantRepository
                .findUserIdsWithActiveMembershipInOtherAccount(userIds, restaurant.getAccountId());
        return memberships.stream()
                .map(m -> new ManagedUser(m, !sharedUserIds.contains(m.getUserId())))
                .toList();
    }

    public ManagedUser getUser(UUID userId) {
        UUID restaurantId = TenantContext.require();
        UserRestaurant membership = requireMembership(userId, restaurantId);
        var restaurant = requireActiveRestaurant(restaurantId);
        return new ManagedUser(membership, isProfileEditable(userId, restaurant.getAccountId()));
    }

    @Transactional
    public CreatedUser createUser(CreateUserCommand cmd) {
        UUID restaurantId = TenantContext.require();

        var existingUser = userRepository.findByEmail(cmd.email());
        if (existingUser.isPresent()) {
            return addExistingUserToRestaurant(existingUser.get(), restaurantId, cmd.roleId());
        }
        String phone = normalizeBlank(cmd.phone());
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw AppException.conflict(ErrorCode.DUPLICATE_PHONE, "Phone number already registered");
        }

        var restaurant = requireActiveRestaurant(restaurantId);
        Role role = resolveAssignableRole(cmd.roleId(), restaurant.getAccountId());

        User user = new User();
        user.setEmail(cmd.email());
        user.setName(cmd.name());
        user.setPhone(phone);
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
            // Que falle el mail no frena el alta: el invitado puede pedir "olvidé mi contraseña".
            log.warn("Set-password email for new user {} failed", user.getId(), e);
        }

        auditService.log(AuditAction.USER_CREATED, "User", user.getId(),
                Map.of("email", cmd.email(), "role", role.getName(), "restaurantId", restaurantId));

        return new CreatedUser(membership, true);
    }

    private CreatedUser addExistingUserToRestaurant(User existingUser, UUID restaurantId, UUID roleId) {
        if (!existingUser.isActive()) {
            throw AppException.forbidden(ErrorCode.USER_INACTIVE, "User is inactive");
        }
        var existingMembership = userRestaurantRepository
                .findByUserIdAndRestaurantId(existingUser.getId(), restaurantId);
        if (existingMembership.isPresent() && existingMembership.get().isActive()) {
            throw AppException.conflict(ErrorCode.USER_ALREADY_IN_RESTAURANT,
                    "User is already an active member of this restaurant");
        }

        var restaurant = requireActiveRestaurant(restaurantId);
        Role role = resolveAssignableRole(roleId, restaurant.getAccountId());

        boolean reactivating = existingMembership.isPresent();
        UserRestaurant membership;
        if (reactivating) {
            membership = existingMembership.get();
            membership.activate();
            membership.setRoleId(role.getId());
            membership.setRoleName(role.getName());
            membership.setRoleIsSystem(false);
            permissionOverrideRepository.replaceAll(existingUser.getId(), restaurantId, List.of());
        } else {
            membership = UserRestaurant.builder()
                    .userId(existingUser.getId())
                    .restaurantId(restaurantId)
                    .roleId(role.getId())
                    .roleName(role.getName())
                    .isActive(true)
                    .build();
        }
        membership = userRestaurantRepository.save(membership);

        try {
            emailService.sendAddedToRestaurant(
                    existingUser.getEmail(), existingUser.getName(), restaurant.getName(), frontendUrl);
        } catch (Exception e) {
            log.warn("Added-to-restaurant email for user {} failed", existingUser.getId(), e);
        }

        AuditAction action = reactivating ? AuditAction.USER_REACTIVATED : AuditAction.USER_CREATED;
        auditService.log(action, "User", existingUser.getId(),
                Map.of("email", existingUser.getEmail(), "role", role.getName(),
                        "restaurantId", restaurantId, "existingUser", true));

        return new CreatedUser(membership, isProfileEditable(existingUser.getId(), restaurant.getAccountId()));
    }

    @Transactional
    public ManagedUser updateUser(UUID userId, UpdateUserCommand cmd) {
        UUID restaurantId = TenantContext.require();

        UserRestaurant membership = requireMembership(userId, restaurantId);
        var restaurant = requireActiveRestaurant(restaurantId);
        Role role = resolveAssignableRole(cmd.roleId(), restaurant.getAccountId());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.USER_NOT_FOUND, "User not found"));

        boolean profileEditable = isProfileEditable(userId, restaurant.getAccountId());

        if (!profileEditable) {
            if (profileChanged(user, cmd)) {
                throw AppException.forbidden(ErrorCode.USER_PROFILE_NOT_EDITABLE,
                        "User profile is shared with another account and cannot be edited here");
            }
        } else {
            user.setName(cmd.name());
            String phone = normalizeBlank(cmd.phone());
            if (phone != null && !phone.equals(user.getPhone())) {
                if (userRepository.existsByPhone(phone)) {
                    throw AppException.conflict(ErrorCode.DUPLICATE_PHONE, "Phone number already registered");
                }
                user.setPhone(phone);
            } else if (phone == null) {
                user.setPhone(null);
            }
            userRepository.save(user);
        }

        membership.setRoleId(role.getId());
        membership.setRoleName(role.getName());
        membership = userRestaurantRepository.save(membership);

        auditService.log(AuditAction.USER_UPDATED, "User", userId,
                Map.of("role", role.getName(), "restaurantId", restaurantId));

        return new ManagedUser(membership, profileEditable);
    }

    // ── El perfil propio (/profile) ───────────────────────────────────────────

    /** El usuario de la sesión: nombre, teléfono y email son suyos en toda la plataforma, no de un local. */
    public User getOwnProfile(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.USER_NOT_FOUND, "User not found"));
    }

    /**
     * Cada uno edita su nombre y su teléfono. Sin la regla de /users (que bloquea a quien trabaja en otra cuenta): ahí el
     * que edita es un admin; acá es la persona. El email no se edita: es su login.
     */
    @Transactional
    public User updateOwnProfile(UUID userId, String name, String phone) {
        User user = getOwnProfile(userId);
        String newPhone = normalizeBlank(phone);
        if (newPhone != null && !newPhone.equals(user.getPhone()) && userRepository.existsByPhone(newPhone)) {
            throw AppException.conflict(ErrorCode.DUPLICATE_PHONE, "Phone number already registered");
        }
        user.setName(name.trim());
        user.setPhone(newPhone);
        user = userRepository.save(user);
        Map<String, Object> payload = new java.util.HashMap<>();   // HashMap: el teléfono puede ser null
        payload.put("name", user.getName());
        payload.put("phone", user.getPhone());
        auditService.log(AuditAction.USER_PROFILE_UPDATED, "User", userId, payload);
        return user;
    }

    private boolean isProfileEditable(UUID userId, UUID accountId) {
        return !userRestaurantRepository.existsActiveMembershipInOtherAccount(userId, accountId);
    }

    private boolean profileChanged(User user, UpdateUserCommand cmd) {
        return !Objects.equals(cmd.name(), user.getName())
                || !Objects.equals(normalizeBlank(cmd.phone()), normalizeBlank(user.getPhone()));
    }

    private String normalizeBlank(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    @Transactional
    public void deactivateUser(UUID userId) {
        UUID restaurantId = TenantContext.require();

        UserRestaurant membership = requireMembership(userId, restaurantId);

        if (membership.isRoleIsSystem()) {
            throw AppException.forbidden(ErrorCode.SYSTEM_ROLE_IMMUTABLE, "Can't deactivate the account owner");
        }

        membership.deactivate();
        userRestaurantRepository.save(membership);

        auditService.log(AuditAction.USER_DEACTIVATED, "User", userId,
                Map.of("restaurantId", restaurantId));
    }

    private Restaurant requireActiveRestaurant(UUID restaurantId) {
        return restaurantRepository.findByIdAndIsActiveTrue(restaurantId)
                .orElseThrow(AppException::sessionRestaurantGone);
    }

    private UserRestaurant requireMembership(UUID userId, UUID restaurantId) {
        return userRestaurantRepository.findByUserIdAndRestaurantId(userId, restaurantId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.USER_NOT_FOUND, "User not found in this restaurant"));
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
        UserRestaurant membership = requireMembership(userId, restaurantId); // valida ownership

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
        UserRestaurant membership = requireMembership(userId, restaurantId); // valida que pertenezca a este restaurante

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

    public record ManagedUser(UserRestaurant membership, boolean profileEditable) {}

    public record CreatedUser(UserRestaurant membership, boolean profileEditable) {}

    public record PermissionOverrideCommand(Permission permission, boolean granted) {}

    public record EffectivePermissions(
            Role role,
            List<PermissionOverride> overrides,
            Set<Permission> effective
    ) {}
}
