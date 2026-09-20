package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.PasswordEncoderPort;
import com.chefcontrol.domain.account.Account;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.plan.PlanTier;
import com.chefcontrol.domain.repository.*;
import com.chefcontrol.domain.restaurant.Restaurant;
import com.chefcontrol.domain.user.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ponytail: la única lógica real de createAdditionalRestaurant es el límite por plan
 * (Feature.MULTI_RESTAURANT) — el resto es armar entidades, no necesita test propio.
 */
@ExtendWith(MockitoExtension.class)
class RestaurantRegistrationServicePlanLimitTest {

    @Mock UserRepository userRepository;
    @Mock AccountRepository accountRepository;
    @Mock RestaurantRepository restaurantRepository;
    @Mock UserRestaurantRepository userRestaurantRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoderPort passwordEncoder;
    @Mock AuditService auditService;

    private final UUID restaurantId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    private RestaurantRegistrationService service() {
        return new RestaurantRegistrationService(userRepository, accountRepository, restaurantRepository,
                userRestaurantRepository, roleRepository, passwordEncoder, auditService);
    }

    @BeforeEach
    void setTenant() {
        TenantContext.set(restaurantId);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private Restaurant currentRestaurant() {
        Restaurant r = new Restaurant();
        r.setId(restaurantId);
        r.setAccountId(accountId);
        return r;
    }

    private Account account(PlanTier plan) {
        return Account.builder().id(accountId).ownerUserId(ownerId).name("Cuenta").plan(plan).build();
    }

    @Test
    void trialPlan_withOneExistingRestaurant_rejectsASecond() {
        when(restaurantRepository.findByIdAndIsActiveTrue(restaurantId)).thenReturn(Optional.of(currentRestaurant()));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account(PlanTier.TRIAL)));
        when(restaurantRepository.findAllByAccountId(accountId)).thenReturn(List.of(currentRestaurant()));

        var cmd = new RestaurantRegistrationService.CreateRestaurantCommand("Segunda sucursal", null);

        assertThatThrownBy(() -> service().createAdditionalRestaurant(cmd))
                .isInstanceOf(AppException.class);

        verify(restaurantRepository, never()).save(any());
    }

    @Test
    void proPlan_withOneExistingRestaurant_allowsASecondAndGrantsOwnerAccess() {
        when(restaurantRepository.findByIdAndIsActiveTrue(restaurantId)).thenReturn(Optional.of(currentRestaurant()));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account(PlanTier.PRO)));
        when(restaurantRepository.findAllByAccountId(accountId)).thenReturn(List.of(currentRestaurant()));
        when(restaurantRepository.existsBySlug(any())).thenReturn(false);
        when(restaurantRepository.save(any())).thenAnswer(inv -> {
            Restaurant r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
        UUID superadminRoleId = UUID.randomUUID();
        when(roleRepository.findByAccountIdAndIsSystemTrue(accountId))
                .thenReturn(Optional.of(Role.builder().id(superadminRoleId).accountId(accountId).name("SUPERADMIN").isSystem(true).build()));

        var cmd = new RestaurantRegistrationService.CreateRestaurantCommand("Segunda sucursal", null);
        Restaurant created = service().createAdditionalRestaurant(cmd);

        assertThat(created.getAccountId()).isEqualTo(accountId);
        verify(userRestaurantRepository).save(argThat(m ->
                m.getUserId().equals(ownerId) && m.getRoleId().equals(superadminRoleId)));
    }

    @Test
    void trialPlan_withNoExistingRestaurants_allowsTheFirstOne() {
        when(restaurantRepository.findByIdAndIsActiveTrue(restaurantId)).thenReturn(Optional.of(currentRestaurant()));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account(PlanTier.TRIAL)));
        when(restaurantRepository.findAllByAccountId(accountId)).thenReturn(List.of());
        when(restaurantRepository.existsBySlug(any())).thenReturn(false);
        when(restaurantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(roleRepository.findByAccountIdAndIsSystemTrue(accountId))
                .thenReturn(Optional.of(Role.builder().id(UUID.randomUUID()).accountId(accountId).name("SUPERADMIN").isSystem(true).build()));

        var cmd = new RestaurantRegistrationService.CreateRestaurantCommand("Primera sucursal", null);

        service().createAdditionalRestaurant(cmd);

        verify(restaurantRepository).save(any());
    }

    // ── Guards de deshabilitar / borrar ──────────────────────────────────────

    @Test
    void cannotDeleteTheRestaurantYouAreCurrentlyIn() {
        when(restaurantRepository.findByIdAndIsActiveTrue(restaurantId)).thenReturn(Optional.of(currentRestaurant()));
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(currentRestaurant()));

        assertThatThrownBy(() -> service().deleteRestaurant(restaurantId))
                .isInstanceOf(AppException.class);

        verify(restaurantRepository, never()).deleteById(any());
    }

    @Test
    void cannotTouchARestaurantFromAnotherAccount() {
        UUID otherId = UUID.randomUUID();
        Restaurant other = new Restaurant();
        other.setId(otherId);
        other.setAccountId(UUID.randomUUID());

        when(restaurantRepository.findByIdAndIsActiveTrue(restaurantId)).thenReturn(Optional.of(currentRestaurant()));
        when(restaurantRepository.findById(otherId)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service().deleteRestaurant(otherId))
                .isInstanceOf(AppException.class);

        verify(restaurantRepository, never()).deleteById(any());
    }

    @Test
    void deletesAnotherRestaurantOfTheSameAccount() {
        UUID otherId = UUID.randomUUID();
        Restaurant other = new Restaurant();
        other.setId(otherId);
        other.setAccountId(accountId);
        other.setName("Sucursal cerrada");
        other.setSlug("sucursal-cerrada");

        when(restaurantRepository.findByIdAndIsActiveTrue(restaurantId)).thenReturn(Optional.of(currentRestaurant()));
        when(restaurantRepository.findById(otherId)).thenReturn(Optional.of(other));

        service().deleteRestaurant(otherId);

        verify(restaurantRepository).deleteById(otherId);
    }
}
