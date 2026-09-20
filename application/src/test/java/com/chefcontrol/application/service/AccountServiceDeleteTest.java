package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.domain.account.Account;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.plan.PlanTier;
import com.chefcontrol.domain.repository.AccountRepository;
import com.chefcontrol.domain.repository.RestaurantRepository;
import com.chefcontrol.domain.repository.UserRepository;
import com.chefcontrol.domain.repository.UserRestaurantRepository;
import com.chefcontrol.domain.restaurant.Restaurant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ponytail: lo único que decide algo acá es quién puede borrar la cuenta — el borrado en sí
 * es una cascada de la base, no lógica de aplicación.
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceDeleteTest {

    @Mock AccountRepository accountRepository;
    @Mock RestaurantRepository restaurantRepository;
    @Mock UserRepository userRepository;
    @Mock UserRestaurantRepository userRestaurantRepository;
    @Mock CurrentUserProvider currentUserProvider;

    private final UUID restaurantId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    private AccountService service() {
        return new AccountService(accountRepository, restaurantRepository, userRepository,
                userRestaurantRepository, currentUserProvider);
    }

    @BeforeEach
    void setUp() {
        TenantContext.set(restaurantId);

        Restaurant r = new Restaurant();
        r.setId(restaurantId);
        r.setAccountId(accountId);
        when(restaurantRepository.findByIdAndIsActiveTrue(restaurantId)).thenReturn(Optional.of(r));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(
                Account.builder().id(accountId).ownerUserId(ownerId).name("Cuenta").plan(PlanTier.PRO).build()));
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void ownerCanDeleteTheAccount() {
        when(currentUserProvider.currentUserId()).thenReturn(ownerId);

        service().deleteCurrentAccount();

        verify(accountRepository).deleteWithAllData(accountId);
    }

    @Test
    void nonOwnerCannotDeleteTheAccount() {
        when(currentUserProvider.currentUserId()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> service().deleteCurrentAccount())
                .isInstanceOf(AppException.class);

        verify(accountRepository, never()).deleteWithAllData(any());
    }
}
