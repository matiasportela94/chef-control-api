package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.payments.SubscriptionService;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.domain.account.Account;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.plan.Feature;
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

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** El reparto de features por plan (decidido el 2026-10-23) y que el corte use el plan efectivo, no el guardado. */
@ExtendWith(MockitoExtension.class)
class AccountServiceRequireFeatureTest {

    @Mock AccountRepository accountRepository;
    @Mock RestaurantRepository restaurantRepository;
    @Mock UserRepository userRepository;
    @Mock UserRestaurantRepository userRestaurantRepository;
    @Mock CurrentUserProvider currentUserProvider;
    @Mock AuditService auditService;
    @Mock SubscriptionService subscriptionService;

    private final UUID restaurantId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private Account account;

    private AccountService service() {
        return new AccountService(accountRepository, restaurantRepository, userRepository,
                userRestaurantRepository, currentUserProvider, auditService, subscriptionService);
    }

    @BeforeEach
    void setUp() {
        TenantContext.set(restaurantId);
        Restaurant r = new Restaurant();
        r.setId(restaurantId);
        r.setAccountId(accountId);
        when(restaurantRepository.findByIdAndIsActiveTrue(restaurantId)).thenReturn(Optional.of(r));
        account = Account.builder().id(accountId).plan(PlanTier.TRIAL).build();
        lenient().when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void freePlanHasNoFoodCostNorAi() {
        assertEquals(ErrorCode.PLAN_FEATURE_REQUIRED,
                assertThrows(AppException.class, () -> service().requireFeature(Feature.RECIPES_AND_FOOD_COST)).getErrorCode());
        assertEquals(ErrorCode.PLAN_FEATURE_REQUIRED,
                assertThrows(AppException.class, () -> service().requireFeature(Feature.AI_INTERPRETER)).getErrorCode());
        assertDoesNotThrow(() -> service().requireFeature(Feature.STOCK_MOVEMENTS));
    }

    @Test
    void starterIncludesFoodCostButNotAdvancedReports() {
        account.setPlan(PlanTier.STARTER);
        assertDoesNotThrow(() -> service().requireFeature(Feature.RECIPES_AND_FOOD_COST));
        assertThrows(AppException.class, () -> service().requireFeature(Feature.ADVANCED_REPORTS));
    }

    @Test
    void expiredPaidPlanFallsBackToFree() {
        account.setPlan(PlanTier.PRO);
        account.setPlanExpiresAt(Instant.now().minusSeconds(1));
        assertThrows(AppException.class, () -> service().requireFeature(Feature.RECIPES_AND_FOOD_COST));
    }

    @Test
    void aSessionOnADeletedRestaurantIsNoSessionAt401() {
        // Borraron el local con la sesión abierta (p. ej. un invitado cuyo dueño cerró la cuenta): era un 500.
        when(restaurantRepository.findByIdAndIsActiveTrue(restaurantId)).thenReturn(Optional.empty());
        assertEquals(ErrorCode.AUTHENTICATION_REQUIRED,
                assertThrows(AppException.class, () -> service().requireFeature(Feature.AI_INTERPRETER)).getErrorCode());
    }
}
