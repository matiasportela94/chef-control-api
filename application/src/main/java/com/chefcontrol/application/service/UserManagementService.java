package com.chefcontrol.application.service;

import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.application.port.PasswordEncoderPort;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.domain.repository.RestaurantRepository;
import com.chefcontrol.domain.repository.RoleRepository;
import com.chefcontrol.domain.repository.UserRepository;
import com.chefcontrol.domain.repository.UserRestaurantRepository;
import com.chefcontrol.domain.user.RoleName;
import com.chefcontrol.domain.user.User;
import com.chefcontrol.domain.user.UserRestaurant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository userRepository;
    private final UserRestaurantRepository userRestaurantRepository;
    private final RoleRepository roleRepository;
    private final RestaurantRepository restaurantRepository;
    private final PasswordEncoderPort passwordEncoder;
    private final PasswordResetService passwordResetService;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;

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
        String callerRole = currentUserProvider.currentRole();

        if (!cmd.role().canBeAssignedBy(RoleName.valueOf(callerRole))) {
            throw AppException.forbidden(ErrorCode.FORBIDDEN, "Managers can only create KITCHEN or READONLY users");
        }

        if (userRepository.existsByEmail(cmd.email())) {
            throw AppException.conflict(ErrorCode.DUPLICATE_EMAIL, "Email already in use");
        }

        if (cmd.phone() != null && userRepository.existsByPhone(cmd.phone())) {
            throw AppException.conflict(ErrorCode.DUPLICATE_PHONE, "Phone number already registered");
        }

        var role = roleRepository.findByName(cmd.role())
                .orElseThrow(() -> new IllegalStateException("Role not found: " + cmd.role()));

        User user = new User();
        user.setEmail(cmd.email());
        user.setName(cmd.name());
        user.setPhone(cmd.phone());
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user = userRepository.save(user);

        var restaurant = restaurantRepository.findByIdAndIsActiveTrue(restaurantId)
                .orElseThrow(() -> new IllegalStateException("Restaurant not found"));

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
                Map.of("email", cmd.email(), "role", cmd.role().name(), "restaurantId", restaurantId));

        return new CreatedUser(membership);
    }

    @Transactional
    public UserRestaurant updateUser(UUID userId, UpdateUserCommand cmd) {
        UUID restaurantId = TenantContext.require();
        String callerRole = currentUserProvider.currentRole();

        UserRestaurant membership = userRestaurantRepository.findByUserIdAndRestaurantId(userId, restaurantId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.USER_NOT_FOUND, "User not found in this restaurant"));

        if (!cmd.role().canBeAssignedBy(RoleName.valueOf(callerRole))) {
            throw AppException.forbidden(ErrorCode.FORBIDDEN, "Managers cannot assign OWNER or MANAGER roles");
        }

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

        var role = roleRepository.findByName(cmd.role())
                .orElseThrow(() -> new IllegalStateException("Role not found: " + cmd.role()));

        membership.setRoleId(role.getId());
        membership.setRoleName(role.getName());
        membership = userRestaurantRepository.save(membership);

        auditService.log(AuditAction.USER_UPDATED, "User", userId,
                Map.of("role", cmd.role().name(), "restaurantId", restaurantId));

        return membership;
    }

    @Transactional
    public void deactivateUser(UUID userId) {
        UUID restaurantId = TenantContext.require();

        UserRestaurant membership = userRestaurantRepository.findByUserIdAndRestaurantId(userId, restaurantId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.USER_NOT_FOUND, "User not found in this restaurant"));

        membership.deactivate();
        userRestaurantRepository.save(membership);

        auditService.log(AuditAction.USER_DEACTIVATED, "User", userId,
                Map.of("restaurantId", restaurantId));
    }

    // ── Commands / Results ────────────────────────────────────────────────────

    public record CreateUserCommand(
            String name,
            String email,
            String phone,
            RoleName role
    ) {}

    public record UpdateUserCommand(
            String name,
            String phone,
            RoleName role
    ) {}

    public record CreatedUser(UserRestaurant membership) {}
}
