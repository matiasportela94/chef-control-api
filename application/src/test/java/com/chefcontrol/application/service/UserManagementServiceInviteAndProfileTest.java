package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.EmailService;
import com.chefcontrol.application.port.PasswordEncoderPort;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.repository.*;
import com.chefcontrol.domain.restaurant.Restaurant;
import com.chefcontrol.domain.user.Role;
import com.chefcontrol.domain.user.User;
import com.chefcontrol.domain.user.UserRestaurant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceInviteAndProfileTest {

    @Mock UserRepository userRepository;
    @Mock UserRestaurantRepository userRestaurantRepository;
    @Mock RoleRepository roleRepository;
    @Mock RestaurantRepository restaurantRepository;
    @Mock PermissionOverrideRepository permissionOverrideRepository;
    @Mock PermissionResolutionService permissionResolutionService;
    @Mock PasswordEncoderPort passwordEncoder;
    @Mock PasswordResetService passwordResetService;
    @Mock EmailService emailService;
    @Mock AuditService auditService;

    private final UUID accountId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();
    private final UUID roleId = UUID.randomUUID();

    private UserManagementService service() {
        return new UserManagementService(userRepository, userRestaurantRepository, roleRepository,
                restaurantRepository, permissionOverrideRepository, permissionResolutionService,
                passwordEncoder, passwordResetService, emailService, auditService);
    }

    @BeforeEach
    void setTenant() {
        TenantContext.set(restaurantId);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private Restaurant activeRestaurant() {
        Restaurant r = new Restaurant();
        r.setId(restaurantId);
        r.setAccountId(accountId);
        r.setName("La Parrilla");
        r.setActive(true);
        return r;
    }

    private Role assignableRole() {
        return Role.builder()
                .id(roleId)
                .accountId(accountId)
                .name("KITCHEN")
                .isSystem(false)
                .build();
    }

    private User existingUser(UUID id, String email, String name, String phone, boolean active) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setName(name);
        u.setPhone(phone);
        u.setActive(active);
        return u;
    }

    @Test
    void createsNewUserAndSendsSetPasswordLinkWhenEmailDoesNotExist() {
        UUID newUserId = UUID.randomUUID();
        Restaurant restaurant = activeRestaurant();
        Role role = assignableRole();

        when(userRepository.findByEmail("nuevo@ejemplo.com")).thenReturn(Optional.empty());
        when(userRepository.existsByPhone("+5491112345678")).thenReturn(false);
        when(restaurantRepository.findByIdAndIsActiveTrue(restaurantId)).thenReturn(Optional.of(restaurant));
        when(roleRepository.findByIdAndAccountId(roleId, accountId)).thenReturn(Optional.of(role));
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(newUserId);
            return u;
        });
        when(userRestaurantRepository.save(any(UserRestaurant.class))).thenAnswer(inv -> inv.getArgument(0));

        var cmd = new UserManagementService.CreateUserCommand("Ana", "nuevo@ejemplo.com", "+5491112345678", roleId);
        var created = service().createUser(cmd);

        assertThat(created.membership().getUserId()).isEqualTo(newUserId);
        assertThat(created.membership().getRoleId()).isEqualTo(roleId);
        verify(passwordResetService).createAndSendSetPasswordToken(any(User.class), eq(restaurant));
        verify(auditService).log(eq(AuditAction.USER_CREATED), eq("User"), eq(newUserId), any());
    }

    @Test
    void rejectsWithUserAlreadyInRestaurantWhenUserIsAlreadyActiveMember() {
        UUID existingUserId = UUID.randomUUID();
        User existing = existingUser(existingUserId, "ya@ejemplo.com", "Juan", "+5491100001111", true);
        UserRestaurant activeMembership = UserRestaurant.builder()
                .userId(existingUserId)
                .restaurantId(restaurantId)
                .roleId(roleId)
                .roleName("KITCHEN")
                .isActive(true)
                .build();

        when(userRepository.findByEmail("ya@ejemplo.com")).thenReturn(Optional.of(existing));
        when(userRestaurantRepository.findByUserIdAndRestaurantId(existingUserId, restaurantId))
                .thenReturn(Optional.of(activeMembership));

        var cmd = new UserManagementService.CreateUserCommand("Otro Nombre", "ya@ejemplo.com", "+5491199998888", roleId);

        assertThatThrownBy(() -> service().createUser(cmd))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_ALREADY_IN_RESTAURANT);

        verify(userRepository, never()).save(any());
        verify(userRestaurantRepository, never()).save(any());
        verifyNoInteractions(passwordResetService);
    }

    @Test
    void rejectsWithUserInactiveWhenExistingUserIsGloballyInactive() {
        UUID inactiveUserId = UUID.randomUUID();
        User inactiveUser = existingUser(inactiveUserId, "baja@ejemplo.com", "Baja", null, false);

        when(userRepository.findByEmail("baja@ejemplo.com")).thenReturn(Optional.of(inactiveUser));

        var cmd = new UserManagementService.CreateUserCommand("Otro", "baja@ejemplo.com", null, roleId);

        assertThatThrownBy(() -> service().createUser(cmd))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_INACTIVE);

        verify(userRepository, never()).save(any());
        verify(userRestaurantRepository, never()).save(any());
    }

    @Test
    void addsExistingUserToRestaurantIgnoringSubmittedNameAndPhone() {
        UUID existingUserId = UUID.randomUUID();
        User existing = existingUser(existingUserId, "dueno@otracuenta.com", "Sofía Real", "+5491122223333", true);
        Restaurant restaurant = activeRestaurant();
        Role role = assignableRole();

        when(userRepository.findByEmail("dueno@otracuenta.com")).thenReturn(Optional.of(existing));
        when(userRestaurantRepository.findByUserIdAndRestaurantId(existingUserId, restaurantId))
                .thenReturn(Optional.empty());
        when(restaurantRepository.findByIdAndIsActiveTrue(restaurantId)).thenReturn(Optional.of(restaurant));
        when(roleRepository.findByIdAndAccountId(roleId, accountId)).thenReturn(Optional.of(role));
        when(userRestaurantRepository.save(any(UserRestaurant.class))).thenAnswer(inv -> {
            UserRestaurant ur = inv.getArgument(0);
            ur.setUserName(existing.getName());
            ur.setUserEmail(existing.getEmail());
            ur.setUserPhone(existing.getPhone());
            return ur;
        });

        var cmd = new UserManagementService.CreateUserCommand(
                "Nombre Ignorado", "dueno@otracuenta.com", "+5491199990000", roleId);
        var created = service().createUser(cmd);

        assertThat(created.membership().getUserId()).isEqualTo(existingUserId);
        assertThat(created.membership().getRoleId()).isEqualTo(roleId);
        assertThat(created.membership().isActive()).isTrue();
        assertThat(created.membership().getUserName()).isEqualTo("Sofía Real");
        assertThat(created.membership().getUserPhone()).isEqualTo("+5491122223333");

        verify(userRepository, never()).save(any());
        verify(userRepository, never()).existsByPhone(any());
        verifyNoInteractions(passwordResetService);
        verify(emailService).sendAddedToRestaurant(
                eq("dueno@otracuenta.com"), eq("Sofía Real"), eq("La Parrilla"), any());
        verify(auditService).log(eq(AuditAction.USER_CREATED), eq("User"), eq(existingUserId), any());
    }

    @Test
    void reactivatesInactiveMembershipWithNewRoleAndClearsStaleOverrides() {
        UUID existingUserId = UUID.randomUUID();
        UUID oldRoleId = UUID.randomUUID();
        User existing = existingUser(existingUserId, "ex@ejemplo.com", "Carlos", "+5491133334444", true);
        UserRestaurant inactiveMembership = UserRestaurant.builder()
                .userId(existingUserId)
                .restaurantId(restaurantId)
                .roleId(oldRoleId)
                .roleName("MANAGER")
                .isActive(false)
                .userName(existing.getName())
                .userEmail(existing.getEmail())
                .userPhone(existing.getPhone())
                .build();
        Restaurant restaurant = activeRestaurant();
        Role newRole = assignableRole();

        when(userRepository.findByEmail("ex@ejemplo.com")).thenReturn(Optional.of(existing));
        when(userRestaurantRepository.findByUserIdAndRestaurantId(existingUserId, restaurantId))
                .thenReturn(Optional.of(inactiveMembership));
        when(restaurantRepository.findByIdAndIsActiveTrue(restaurantId)).thenReturn(Optional.of(restaurant));
        when(roleRepository.findByIdAndAccountId(roleId, accountId)).thenReturn(Optional.of(newRole));
        when(userRestaurantRepository.save(any(UserRestaurant.class))).thenAnswer(inv -> inv.getArgument(0));

        var cmd = new UserManagementService.CreateUserCommand(
                "Nombre Nuevo Ignorado", "ex@ejemplo.com", "+5491100000000", roleId);
        var created = service().createUser(cmd);

        assertThat(created.membership().isActive()).isTrue();
        assertThat(created.membership().getRoleId()).isEqualTo(roleId);
        assertThat(created.membership().getRoleName()).isEqualTo("KITCHEN");

        verify(permissionOverrideRepository).replaceAll(existingUserId, restaurantId, List.of());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordResetService);
        verify(emailService).sendAddedToRestaurant(
                eq("ex@ejemplo.com"), eq("Carlos"), eq("La Parrilla"), any());
        verify(auditService).log(eq(AuditAction.USER_REACTIVATED), eq("User"), eq(existingUserId), any());
    }

    @Test
    void doesNotFailUserAdditionWhenNotificationEmailThrows() {
        UUID existingUserId = UUID.randomUUID();
        User existing = existingUser(existingUserId, "mail@ejemplo.com", "Lucía", null, true);
        Restaurant restaurant = activeRestaurant();
        Role role = assignableRole();

        when(userRepository.findByEmail("mail@ejemplo.com")).thenReturn(Optional.of(existing));
        when(userRestaurantRepository.findByUserIdAndRestaurantId(existingUserId, restaurantId))
                .thenReturn(Optional.empty());
        when(restaurantRepository.findByIdAndIsActiveTrue(restaurantId)).thenReturn(Optional.of(restaurant));
        when(roleRepository.findByIdAndAccountId(roleId, accountId)).thenReturn(Optional.of(role));
        when(userRestaurantRepository.save(any(UserRestaurant.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("SMTP down"))
                .when(emailService).sendAddedToRestaurant(any(), any(), any(), any());

        var cmd = new UserManagementService.CreateUserCommand("X", "mail@ejemplo.com", null, roleId);
        var created = service().createUser(cmd);

        assertThat(created.membership().getUserId()).isEqualTo(existingUserId);
        verify(auditService).log(eq(AuditAction.USER_CREATED), eq("User"), eq(existingUserId), any());
    }

    @Test
    void marksProfileEditableFalseOnlyForUsersWithActiveMembershipInAnotherAccount() {
        UUID ownUserId = UUID.randomUUID();
        UUID sharedUserId = UUID.randomUUID();
        Restaurant restaurant = activeRestaurant();
        UserRestaurant ownMembership = UserRestaurant.builder()
                .userId(ownUserId).restaurantId(restaurantId).roleId(roleId).roleName("KITCHEN").isActive(true).build();
        UserRestaurant sharedMembership = UserRestaurant.builder()
                .userId(sharedUserId).restaurantId(restaurantId).roleId(roleId).roleName("KITCHEN").isActive(true).build();

        when(restaurantRepository.findByIdAndIsActiveTrue(restaurantId)).thenReturn(Optional.of(restaurant));
        when(userRestaurantRepository.findActiveByRestaurantId(restaurantId))
                .thenReturn(List.of(ownMembership, sharedMembership));
        when(userRestaurantRepository.findUserIdsWithActiveMembershipInOtherAccount(
                List.of(ownUserId, sharedUserId), accountId))
                .thenReturn(Set.of(sharedUserId));

        var listed = service().listUsers();

        assertThat(listed).hasSize(2);
        assertThat(listed.get(0).membership().getUserId()).isEqualTo(ownUserId);
        assertThat(listed.get(0).profileEditable()).isTrue();
        assertThat(listed.get(1).membership().getUserId()).isEqualTo(sharedUserId);
        assertThat(listed.get(1).profileEditable()).isFalse();

        when(userRestaurantRepository.findByUserIdAndRestaurantId(sharedUserId, restaurantId))
                .thenReturn(Optional.of(sharedMembership));
        when(userRestaurantRepository.existsActiveMembershipInOtherAccount(sharedUserId, accountId))
                .thenReturn(true);

        var fetched = service().getUser(sharedUserId);
        assertThat(fetched.profileEditable()).isFalse();
    }

    @Test
    void allowsUpdatingRoleOnlyWhenProfileIsNotEditable() {
        UUID sharedUserId = UUID.randomUUID();
        UUID oldRoleId = UUID.randomUUID();
        Restaurant restaurant = activeRestaurant();
        Role newRole = assignableRole();
        User sharedUser = existingUser(sharedUserId, "google@otracuenta.com", "Sofía", null, true);
        UserRestaurant membership = UserRestaurant.builder()
                .userId(sharedUserId).restaurantId(restaurantId).roleId(oldRoleId).roleName("READONLY").isActive(true).build();

        when(userRestaurantRepository.findByUserIdAndRestaurantId(sharedUserId, restaurantId))
                .thenReturn(Optional.of(membership));
        when(restaurantRepository.findByIdAndIsActiveTrue(restaurantId)).thenReturn(Optional.of(restaurant));
        when(roleRepository.findByIdAndAccountId(roleId, accountId)).thenReturn(Optional.of(newRole));
        when(userRepository.findById(sharedUserId)).thenReturn(Optional.of(sharedUser));
        when(userRestaurantRepository.existsActiveMembershipInOtherAccount(sharedUserId, accountId))
                .thenReturn(true);
        when(userRestaurantRepository.save(any(UserRestaurant.class))).thenAnswer(inv -> inv.getArgument(0));

        // El front manda phone "" cuando el usuario no tiene teléfono cargado (ej. alta con Google).
        var cmd = new UserManagementService.UpdateUserCommand("Sofía", "", roleId);
        var updated = service().updateUser(sharedUserId, cmd);

        assertThat(updated.profileEditable()).isFalse();
        assertThat(updated.membership().getRoleId()).isEqualTo(roleId);
        verify(userRepository, never()).save(any());
        verify(userRepository, never()).existsByPhone(any());
        verify(auditService).log(eq(AuditAction.USER_UPDATED), eq("User"), eq(sharedUserId), any());
    }

    @Test
    void rejectsNameChangeWhenProfileIsNotEditable() {
        UUID sharedUserId = UUID.randomUUID();
        Restaurant restaurant = activeRestaurant();
        Role role = assignableRole();
        User sharedUser = existingUser(sharedUserId, "a@otra.com", "Sofía", "+5491122223333", true);
        UserRestaurant membership = UserRestaurant.builder()
                .userId(sharedUserId).restaurantId(restaurantId).roleId(roleId).roleName("KITCHEN").isActive(true).build();

        when(userRestaurantRepository.findByUserIdAndRestaurantId(sharedUserId, restaurantId))
                .thenReturn(Optional.of(membership));
        when(restaurantRepository.findByIdAndIsActiveTrue(restaurantId)).thenReturn(Optional.of(restaurant));
        when(roleRepository.findByIdAndAccountId(roleId, accountId)).thenReturn(Optional.of(role));
        when(userRepository.findById(sharedUserId)).thenReturn(Optional.of(sharedUser));
        when(userRestaurantRepository.existsActiveMembershipInOtherAccount(sharedUserId, accountId))
                .thenReturn(true);

        var cmd = new UserManagementService.UpdateUserCommand("Otro Nombre", "+5491122223333", roleId);

        assertThatThrownBy(() -> service().updateUser(sharedUserId, cmd))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_PROFILE_NOT_EDITABLE);

        verify(userRepository, never()).save(any());
        verify(userRestaurantRepository, never()).save(any());
    }

    @Test
    void rejectsPhoneChangeWhenProfileIsNotEditable() {
        UUID sharedUserId = UUID.randomUUID();
        Restaurant restaurant = activeRestaurant();
        Role role = assignableRole();
        User sharedUser = existingUser(sharedUserId, "a@otra.com", "Sofía", "+5491122223333", true);
        UserRestaurant membership = UserRestaurant.builder()
                .userId(sharedUserId).restaurantId(restaurantId).roleId(roleId).roleName("KITCHEN").isActive(true).build();

        when(userRestaurantRepository.findByUserIdAndRestaurantId(sharedUserId, restaurantId))
                .thenReturn(Optional.of(membership));
        when(restaurantRepository.findByIdAndIsActiveTrue(restaurantId)).thenReturn(Optional.of(restaurant));
        when(roleRepository.findByIdAndAccountId(roleId, accountId)).thenReturn(Optional.of(role));
        when(userRepository.findById(sharedUserId)).thenReturn(Optional.of(sharedUser));
        when(userRestaurantRepository.existsActiveMembershipInOtherAccount(sharedUserId, accountId))
                .thenReturn(true);

        var cmd = new UserManagementService.UpdateUserCommand("Sofía", "+5491199999999", roleId);

        assertThatThrownBy(() -> service().updateUser(sharedUserId, cmd))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_PROFILE_NOT_EDITABLE);

        verify(userRepository, never()).save(any());
        verify(userRestaurantRepository, never()).save(any());
    }

    @Test
    void allowsEditingNameAndPhoneWhenUserOnlyBelongsToCurrentAccount() {
        UUID ownUserId = UUID.randomUUID();
        Restaurant restaurant = activeRestaurant();
        Role role = assignableRole();
        User ownUser = existingUser(ownUserId, "propio@cuenta.com", "Juan", "+5491111111111", true);
        UserRestaurant membership = UserRestaurant.builder()
                .userId(ownUserId).restaurantId(restaurantId).roleId(roleId).roleName("KITCHEN").isActive(true).build();

        when(userRestaurantRepository.findByUserIdAndRestaurantId(ownUserId, restaurantId))
                .thenReturn(Optional.of(membership));
        when(restaurantRepository.findByIdAndIsActiveTrue(restaurantId)).thenReturn(Optional.of(restaurant));
        when(roleRepository.findByIdAndAccountId(roleId, accountId)).thenReturn(Optional.of(role));
        when(userRepository.findById(ownUserId)).thenReturn(Optional.of(ownUser));
        when(userRestaurantRepository.existsActiveMembershipInOtherAccount(ownUserId, accountId))
                .thenReturn(false);
        when(userRepository.existsByPhone("+5491122222222")).thenReturn(false);
        when(userRestaurantRepository.save(any(UserRestaurant.class))).thenAnswer(inv -> inv.getArgument(0));

        var cmd = new UserManagementService.UpdateUserCommand("Juan Nuevo", "+5491122222222", roleId);
        var updated = service().updateUser(ownUserId, cmd);

        assertThat(updated.profileEditable()).isTrue();
        assertThat(ownUser.getName()).isEqualTo("Juan Nuevo");
        assertThat(ownUser.getPhone()).isEqualTo("+5491122222222");
        verify(userRepository).save(ownUser);
    }
}
