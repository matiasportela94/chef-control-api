package com.chefcontrol.application.payments;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.payments.PaymentProvider.CreatedSubscription;
import com.chefcontrol.application.payments.PaymentProvider.ProviderSubscription;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.application.port.EmailService;
import com.chefcontrol.domain.account.Account;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.payments.BillingPeriod;
import com.chefcontrol.domain.payments.Subscription;
import com.chefcontrol.domain.payments.SubscriptionRepository;
import com.chefcontrol.domain.payments.SubscriptionStatus;
import com.chefcontrol.domain.plan.PlanTier;
import com.chefcontrol.domain.repository.AccountRepository;
import com.chefcontrol.domain.repository.RestaurantRepository;
import com.chefcontrol.domain.repository.UserRepository;
import com.chefcontrol.domain.restaurant.Restaurant;
import com.chefcontrol.domain.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SubscriptionServiceTest {

    private final UUID restaurantId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    private final PaymentProvider provider = mock(PaymentProvider.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final RestaurantRepository restaurants = mock(RestaurantRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
    private final AuditService audit = mock(AuditService.class);
    private final EmailService email = mock(EmailService.class);
    private final FakeSubscriptions subscriptions = new FakeSubscriptions();
    private Account account;

    /** Repositorio en memoria: lo que importa acá es el estado que queda, no las llamadas. */
    static class FakeSubscriptions implements SubscriptionRepository {
        final List<Subscription> all = new ArrayList<>();

        public Subscription save(Subscription s) {
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
                all.add(s);
            }
            return s;
        }
        public Optional<Subscription> findByProviderSubscriptionId(String p, String id) {
            return all.stream().filter(s -> id.equals(s.getProviderSubscriptionId())).findFirst();
        }
        public Optional<Subscription> findLiveByAccountId(UUID accountId) {
            return all.stream().filter(s -> s.getAccountId().equals(accountId) && s.isLive()).findFirst();
        }
        public Optional<Subscription> findLatestByAccountId(UUID accountId) {
            return all.stream().filter(s -> s.getAccountId().equals(accountId)).reduce((a, b) -> b);
        }
        public List<Subscription> findOverdue(Instant now) {
            return all.stream().filter(s -> s.getStatus() == SubscriptionStatus.PAST_DUE
                    || (s.getStatus() == SubscriptionStatus.ACTIVE && !s.getCurrentPeriodEnd().isAfter(now))).toList();
        }
    }

    /** "Ahora" de los tests: 20/08/2026 09:00 hora argentina. */
    private final Instant now = Instant.parse("2026-08-20T12:00:00Z");

    private SubscriptionService service() {
        return new SubscriptionService(subscriptions, provider, accounts, restaurants, users, currentUser, audit, email,
                Clock.fixed(now, ZoneOffset.UTC),
                new BigDecimal("100000"), new BigDecimal("170000"), new BigDecimal("225000"),
                new BigDecimal("15"), "https://app.test");
    }

    @BeforeEach
    void setUp() {
        TenantContext.set(restaurantId);
        Restaurant r = new Restaurant();
        r.setId(restaurantId);
        r.setAccountId(accountId);
        when(restaurants.findByIdAndIsActiveTrue(restaurantId)).thenReturn(Optional.of(r));
        account = Account.builder().id(accountId).ownerUserId(ownerId).name("Cuenta").plan(PlanTier.TRIAL).build();
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));
        when(accounts.save(any())).thenAnswer(i -> i.getArgument(0));
        when(currentUser.currentUserId()).thenReturn(ownerId);
        User owner = new User();
        owner.setId(ownerId);
        owner.setEmail("dueno@resto.com");
        owner.setName("Ana");
        when(users.findById(ownerId)).thenReturn(Optional.of(owner));
        when(provider.name()).thenReturn("MERCADOPAGO");
        when(provider.createSubscription(any())).thenReturn(new CreatedSubscription("mp-1", "https://mp/checkout"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Subscription existing(SubscriptionStatus status, String providerId) {
        return subscriptions.save(Subscription.builder().accountId(accountId).planTier(PlanTier.STARTER).billingPeriod(BillingPeriod.MONTHLY)
                .status(status).provider("MERCADOPAGO").providerSubscriptionId(providerId)
                .amountArs(new BigDecimal("100000")).payerEmail("dueno@resto.com").build());
    }

    private static ErrorCode codeOf(AppException e) {
        return e.getErrorCode();
    }

    // ---- crear

    @Test
    void createStartsPendingSubscriptionAndDoesNotChangeThePlanYet() {
        var checkout = service().createSubscription(PlanTier.PRO, BillingPeriod.MONTHLY);

        assertEquals("https://mp/checkout", checkout.checkoutUrl());
        Subscription s = checkout.subscription();
        assertEquals(SubscriptionStatus.PENDING, s.getStatus());
        assertEquals("mp-1", s.getProviderSubscriptionId());
        assertEquals(new BigDecimal("170000"), s.getAmountArs());
        assertEquals("dueno@resto.com", s.getPayerEmail());
        assertEquals(PlanTier.TRIAL, account.getPlan(), "el plan cambia recién cuando MP autoriza");
        verify(provider).createSubscription(argThat(r -> r.externalReference().equals(s.getId().toString())
                && r.backUrl().equals("https://app.test/plan")));
    }

    @Test
    void onlyTheOwnerCanSubscribe() {
        when(currentUser.currentUserId()).thenReturn(UUID.randomUUID());
        assertEquals(ErrorCode.NOT_ACCOUNT_OWNER,
                codeOf(assertThrows(AppException.class, () -> service().createSubscription(PlanTier.STARTER, BillingPeriod.MONTHLY))));
        verifyNoInteractions(provider);
    }

    @Test
    void planWithoutPriceCannotBeSubscribed() {
        assertEquals(ErrorCode.INVALID_SUBSCRIPTION_PLAN,
                codeOf(assertThrows(AppException.class, () -> service().createSubscription(PlanTier.TRIAL, BillingPeriod.MONTHLY))));
    }

    @Test
    void activeSubscriptionBlocksANewOneButAbandonedCheckoutIsReplaced() {
        Subscription active = existing(SubscriptionStatus.ACTIVE, "mp-old");
        assertEquals(ErrorCode.SUBSCRIPTION_ALREADY_ACTIVE,
                codeOf(assertThrows(AppException.class, () -> service().createSubscription(PlanTier.PRO, BillingPeriod.MONTHLY))));

        active.setStatus(SubscriptionStatus.PENDING);
        service().createSubscription(PlanTier.PRO, BillingPeriod.MONTHLY);

        verify(provider).cancelSubscription("mp-old");
        assertEquals(SubscriptionStatus.CANCELLED, active.getStatus());
        assertEquals(1, subscriptions.all.stream().filter(Subscription::isLive).count());
    }

    @Test
    void providerFailureOnCreateIsReportedAsUnavailable() {
        when(provider.createSubscription(any())).thenThrow(new PaymentProviderException("boom"));
        assertEquals(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE,
                codeOf(assertThrows(AppException.class, () -> service().createSubscription(PlanTier.STARTER, BillingPeriod.MONTHLY))));
    }

    // ---- sincronizar (webhook)

    @Test
    void authorizedAtProviderActivatesAndRaisesThePlan() {
        existing(SubscriptionStatus.PENDING, "mp-1");
        when(provider.getSubscription("mp-1")).thenReturn(new ProviderSubscription("mp-1", SubscriptionStatus.ACTIVE, null));

        service().syncSubscription("mp-1");

        assertEquals(SubscriptionStatus.ACTIVE, subscriptions.all.get(0).getStatus());
        assertEquals(PlanTier.STARTER, account.getPlan());
    }

    @Test
    void activationSendsTheWelcomeOnceAndARenewalSendsNothing() {
        existing(SubscriptionStatus.PENDING, "mp-1");
        when(provider.getSubscription("mp-1")).thenReturn(new ProviderSubscription("mp-1", SubscriptionStatus.ACTIVE,
                Instant.parse("2026-09-20T12:00:00Z")));
        service().syncSubscription("mp-1");

        verify(email).sendSubscriptionWelcome("dueno@resto.com", "Ana", "Starter", new BigDecimal("100000"),
                BillingPeriod.MONTHLY, "https://app.test/dashboard");

        when(provider.getSubscription("mp-1")).thenReturn(new ProviderSubscription("mp-1", SubscriptionStatus.ACTIVE,
                Instant.parse("2026-10-20T12:00:00Z")));
        service().syncSubscription("mp-1"); // renovación: el comprobante lo manda MP
        service().syncSubscription("mp-1"); // aviso repetido
        verifyNoMoreInteractions(email);
    }

    @Test
    void cancelledAtProviderDropsToTrial() {
        existing(SubscriptionStatus.ACTIVE, "mp-1");
        account.setPlan(PlanTier.STARTER);
        when(provider.getSubscription("mp-1")).thenReturn(new ProviderSubscription("mp-1", SubscriptionStatus.CANCELLED, null));

        service().syncSubscription("mp-1");

        assertEquals(SubscriptionStatus.CANCELLED, subscriptions.all.get(0).getStatus());
        assertEquals(PlanTier.TRIAL, account.getPlan());
    }

    @Test
    void cancellationDoesNotOverrideAPlanSetByHand() {
        existing(SubscriptionStatus.ACTIVE, "mp-1");
        account.setPlan(PlanTier.ENTERPRISE);
        when(provider.getSubscription("mp-1")).thenReturn(new ProviderSubscription("mp-1", SubscriptionStatus.CANCELLED, null));

        service().syncSubscription("mp-1");

        assertEquals(PlanTier.ENTERPRISE, account.getPlan());
    }

    @Test
    void aLateNoticeCannotResurrectACancelledSubscription() {
        existing(SubscriptionStatus.CANCELLED, "mp-1");
        when(provider.getSubscription("mp-1")).thenReturn(new ProviderSubscription("mp-1", SubscriptionStatus.ACTIVE, null));

        service().syncSubscription("mp-1");

        assertEquals(SubscriptionStatus.CANCELLED, subscriptions.all.get(0).getStatus());
        assertEquals(PlanTier.TRIAL, account.getPlan());
    }

    @Test
    void unknownProviderStateOrUnknownSubscriptionChangeNothing() {
        existing(SubscriptionStatus.ACTIVE, "mp-1");
        when(provider.getSubscription("mp-1")).thenReturn(new ProviderSubscription("mp-1", null, null));
        service().syncSubscription("mp-1");
        assertEquals(SubscriptionStatus.ACTIVE, subscriptions.all.get(0).getStatus());

        service().syncSubscription("mp-desconocida");
        verify(provider, never()).getSubscription("mp-desconocida");
    }

    // ---- precios y período

    @Test
    void annualIsTwelveMonthsWithFifteenPercentOffAndTrialCannotBeBought() {
        var service = service();
        assertEquals(new BigDecimal("100000"), service.priceFor(PlanTier.STARTER, BillingPeriod.MONTHLY));
        assertEquals(new BigDecimal("1020000"), service.priceFor(PlanTier.STARTER, BillingPeriod.ANNUAL));
        assertEquals(new BigDecimal("1734000"), service.priceFor(PlanTier.PRO, BillingPeriod.ANNUAL));
        assertEquals(new BigDecimal("2295000"), service.priceFor(PlanTier.ENTERPRISE, BillingPeriod.ANNUAL));
        assertNull(service.priceFor(PlanTier.TRIAL, BillingPeriod.ANNUAL));
    }

    @Test
    void annualSubscriptionIsChargedOnceEveryTwelveMonths() {
        var checkout = service().createSubscription(PlanTier.PRO, BillingPeriod.ANNUAL);

        assertEquals(new BigDecimal("1734000"), checkout.subscription().getAmountArs());
        assertEquals(BillingPeriod.ANNUAL, checkout.subscription().getBillingPeriod());
        verify(provider).createSubscription(argThat(r -> r.frequencyMonths() == 12
                && r.amountArs().equals(new BigDecimal("1734000"))));
    }

    @Test
    void activationTakesTheProviderPeriodEndOrEstimatesIt() {
        existing(SubscriptionStatus.PENDING, "mp-1");
        Instant next = Instant.parse("2026-09-15T20:00:03Z");
        when(provider.getSubscription("mp-1")).thenReturn(new ProviderSubscription("mp-1", SubscriptionStatus.ACTIVE, next));
        service().syncSubscription("mp-1");
        assertEquals(next, subscriptions.all.get(0).getCurrentPeriodEnd());

        // si MP no informa la fecha: hoy + un mes de calendario
        subscriptions.all.clear();
        existing(SubscriptionStatus.PENDING, "mp-1");
        when(provider.getSubscription("mp-1")).thenReturn(new ProviderSubscription("mp-1", SubscriptionStatus.ACTIVE, null));
        service().syncSubscription("mp-1");
        assertEquals(Instant.parse("2026-09-20T12:00:00Z"), subscriptions.all.get(0).getCurrentPeriodEnd());
    }

    @Test
    void aRenewalJustMovesThePeriodEnd() {
        Subscription s = existing(SubscriptionStatus.ACTIVE, "mp-1");
        s.setCurrentPeriodEnd(Instant.parse("2026-09-15T20:00:03Z"));
        Instant renewed = Instant.parse("2026-10-15T20:00:03Z");
        when(provider.getSubscription("mp-1")).thenReturn(new ProviderSubscription("mp-1", SubscriptionStatus.ACTIVE, renewed));

        service().syncSubscription("mp-1");

        assertEquals(renewed, s.getCurrentPeriodEnd());
        assertEquals(SubscriptionStatus.ACTIVE, s.getStatus());
    }

    // ---- cancelar conserva lo pagado

    @Test
    void cancellingKeepsThePaidPlanUntilTheLastPaidSecond() {
        Subscription s = existing(SubscriptionStatus.ACTIVE, "mp-1");
        Instant periodEnd = Instant.parse("2026-09-15T20:00:03Z"); // pagó el 15/08 17:00:03 (hora AR)
        s.setCurrentPeriodEnd(periodEnd);
        account.setPlan(PlanTier.STARTER);

        service().cancelSubscription();

        verify(provider).cancelSubscription("mp-1");
        assertEquals(SubscriptionStatus.CANCELLED, s.getStatus());
        assertEquals(PlanTier.STARTER, account.getPlan(), "el plan guardado no baja");
        assertEquals(periodEnd, account.getPlanExpiresAt());
        assertEquals(PlanTier.STARTER, account.effectivePlan(Instant.parse("2026-09-15T20:00:02Z")));
        assertEquals(PlanTier.TRIAL, account.effectivePlan(Instant.parse("2026-09-15T20:00:03Z")));
    }

    @Test
    void cancellingWithNothingPaidLeftDropsToTrialRightAway() {
        Subscription s = existing(SubscriptionStatus.ACTIVE, "mp-1");
        s.setCurrentPeriodEnd(Instant.parse("2026-08-19T12:00:00Z")); // ya venció
        account.setPlan(PlanTier.STARTER);

        service().cancelSubscription();

        assertEquals(PlanTier.TRIAL, account.getPlan());
        assertNull(account.getPlanExpiresAt());
    }

    @Test
    void cancellingACheckoutNeverPaidDoesNotTouchThePlan() {
        existing(SubscriptionStatus.PENDING, "mp-1");

        service().cancelSubscription();

        assertEquals(PlanTier.TRIAL, account.getPlan());
        assertNull(account.getPlanExpiresAt());
    }

    @Test
    void cancelledAtProviderWithPaidPeriodLeftAlsoKeepsThePlan() {
        Subscription s = existing(SubscriptionStatus.ACTIVE, "mp-1");
        Instant periodEnd = Instant.parse("2026-09-15T20:00:03Z");
        s.setCurrentPeriodEnd(periodEnd);
        account.setPlan(PlanTier.STARTER);
        when(provider.getSubscription("mp-1")).thenReturn(new ProviderSubscription("mp-1", SubscriptionStatus.CANCELLED, null));

        service().syncSubscription("mp-1");

        assertEquals(PlanTier.STARTER, account.getPlan());
        assertEquals(periodEnd, account.getPlanExpiresAt());
    }

    @Test
    void subscribingAgainDuringTheGracePeriodRemovesTheCut() {
        Subscription old = existing(SubscriptionStatus.CANCELLED, "mp-old");
        old.setCurrentPeriodEnd(Instant.parse("2026-09-15T20:00:03Z"));
        account.setPlan(PlanTier.STARTER);
        account.setPlanExpiresAt(Instant.parse("2026-09-15T20:00:03Z"));

        var checkout = service().createSubscription(PlanTier.PRO, BillingPeriod.MONTHLY);
        when(provider.getSubscription("mp-1")).thenReturn(new ProviderSubscription("mp-1", SubscriptionStatus.ACTIVE, null));
        service().syncSubscription("mp-1");

        assertEquals(PlanTier.PRO, account.getPlan());
        assertNull(account.getPlanExpiresAt());
        assertEquals("mp-1", checkout.subscription().getProviderSubscriptionId());
    }

    // ---- cancelar y cierre de cuenta

    @Test
    void cancelStopsTheChargeAndDropsToTrial() {
        existing(SubscriptionStatus.ACTIVE, "mp-1");
        account.setPlan(PlanTier.STARTER);

        var cancelled = service().cancelSubscription();

        verify(provider).cancelSubscription("mp-1");
        assertEquals(SubscriptionStatus.CANCELLED, cancelled.getStatus());
        assertEquals(PlanTier.TRIAL, account.getPlan());
    }

    @Test
    void cancelWithoutSubscriptionIsNotFound() {
        assertEquals(ErrorCode.SUBSCRIPTION_NOT_FOUND,
                codeOf(assertThrows(AppException.class, () -> service().cancelSubscription())));
    }

    @Test
    void closingTheAccountAbortsIfTheChargeCannotBeCancelled() {
        existing(SubscriptionStatus.ACTIVE, "mp-1");
        doThrow(new PaymentProviderException("down")).when(provider).cancelSubscription(anyString());

        assertEquals(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE,
                codeOf(assertThrows(AppException.class, () -> service().cancelBeforeAccountClosure(accountId))));
    }

    @Test
    void closingTheAccountWithoutSubscriptionDoesNotTouchTheProvider() {
        service().cancelBeforeAccountClosure(accountId);
        verifyNoInteractions(provider);
    }

    // ---- mora (tarea diaria y avisos de cobro)

    /** Venció ayer, 19/08 a las 17:00:03 hora AR, y MP sigue informando esa fecha como el próximo cobro: no entró. */
    private static final Instant DUE = Instant.parse("2026-08-19T20:00:03Z");

    private Subscription overdue(SubscriptionStatus status, Instant periodEnd) {
        Subscription s = existing(status, "mp-1");
        s.setCurrentPeriodEnd(periodEnd);
        account.setPlan(PlanTier.STARTER);
        if (status == SubscriptionStatus.PAST_DUE) account.setPlanExpiresAt(periodEnd.plus(SubscriptionService.GRACE));
        when(provider.getSubscription("mp-1")).thenReturn(new ProviderSubscription("mp-1", SubscriptionStatus.ACTIVE, periodEnd));
        return s;
    }

    @Test
    void anUnpaidRenewalEntersArrearsWithThreeDaysOfGraceAndAReminder() {
        Subscription s = overdue(SubscriptionStatus.ACTIVE, DUE);

        service().collectOverdueSubscription(s);

        Instant accessUntil = Instant.parse("2026-08-22T20:00:03Z");
        assertEquals(SubscriptionStatus.PAST_DUE, s.getStatus());
        assertEquals(DUE, s.getCurrentPeriodEnd(), "el fin de período es lo que se debe: no se mueve");
        assertEquals(accessUntil, account.getPlanExpiresAt());
        assertEquals(PlanTier.STARTER, account.effectivePlan(Instant.parse("2026-08-22T20:00:02Z")));
        assertEquals(PlanTier.TRIAL, account.effectivePlan(accessUntil));
        verify(email).sendPaymentFailedReminder("dueno@resto.com", "Ana", "Starter", accessUntil, "https://app.test/plan");
        verify(audit).log(eq(AuditAction.SUBSCRIPTION_PAST_DUE), eq("Subscription"), eq(s.getId()), any());
    }

    @Test
    void eachDayOfGraceSendsOneMoreReminderWithoutChangingTheCut() {
        Subscription s = overdue(SubscriptionStatus.PAST_DUE, DUE);

        service().collectOverdueSubscription(s);

        assertEquals(SubscriptionStatus.PAST_DUE, s.getStatus());
        assertEquals(DUE.plus(SubscriptionService.GRACE), account.getPlanExpiresAt());
        verify(email).sendPaymentFailedReminder(any(), any(), any(), eq(DUE.plus(SubscriptionService.GRACE)), any());
        verify(provider, never()).cancelSubscription(anyString());
    }

    @Test
    void whenTheGraceRunsOutItIsCancelledAtTheProviderAndDropsToTrial() {
        Instant due = now.minus(SubscriptionService.GRACE); // la gracia termina justo ahora
        Subscription s = overdue(SubscriptionStatus.PAST_DUE, due);

        service().collectOverdueSubscription(s);

        verify(provider).cancelSubscription("mp-1");
        assertEquals(SubscriptionStatus.CANCELLED, s.getStatus());
        assertEquals(PlanTier.TRIAL, account.getPlan());
        assertNull(account.getPlanExpiresAt());
        verify(email).sendPlanDowngradedToTrial("dueno@resto.com", "Ana", "Starter", "https://app.test/plan");
        verify(email, never()).sendPaymentFailedReminder(any(), any(), any(), any(), any());
    }

    @Test
    void ifTheProviderCannotCancelTheArrearsWaitForTomorrow() {
        Subscription s = overdue(SubscriptionStatus.PAST_DUE, now.minus(SubscriptionService.GRACE));
        doThrow(new PaymentProviderException("down")).when(provider).cancelSubscription(anyString());

        assertThrows(AppException.class, () -> service().collectOverdueSubscription(s));

        assertEquals(SubscriptionStatus.PAST_DUE, s.getStatus());
        verify(email, never()).sendPlanDowngradedToTrial(any(), any(), any(), any());
    }

    @Test
    void payingDuringTheGraceRestoresThePlanAsSoonAsTheNoticeArrives() {
        Subscription s = overdue(SubscriptionStatus.PAST_DUE, DUE);
        Instant renewed = Instant.parse("2026-09-19T20:00:03Z");
        when(provider.getSubscription("mp-1")).thenReturn(new ProviderSubscription("mp-1", SubscriptionStatus.ACTIVE, renewed));

        service().syncSubscription("mp-1");

        assertEquals(SubscriptionStatus.ACTIVE, s.getStatus());
        assertEquals(renewed, s.getCurrentPeriodEnd());
        assertEquals(PlanTier.STARTER, account.getPlan());
        assertNull(account.getPlanExpiresAt());
        verify(email).sendPaymentRecovered("dueno@resto.com", "Ana", "Starter", "https://app.test/dashboard");
        verify(email, never()).sendSubscriptionWelcome(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aNoticeWhileTheChargeIsStillDueDoesNotLiftTheArrears() {
        Subscription s = overdue(SubscriptionStatus.PAST_DUE, DUE);

        service().syncSubscription("mp-1");

        assertEquals(SubscriptionStatus.PAST_DUE, s.getStatus());
        assertEquals(DUE.plus(SubscriptionService.GRACE), account.getPlanExpiresAt());
        verifyNoInteractions(audit, email);
    }

    @Test
    void withoutANextPaymentDateArrearsAreNeverPresumed() {
        Subscription s = overdue(SubscriptionStatus.ACTIVE, DUE);
        when(provider.getSubscription("mp-1")).thenReturn(new ProviderSubscription("mp-1", SubscriptionStatus.ACTIVE, null));

        service().collectOverdueSubscription(s);

        assertEquals(SubscriptionStatus.ACTIVE, s.getStatus());
        assertNull(account.getPlanExpiresAt());
        verifyNoInteractions(email);
    }

    @Test
    void cancellingDuringArrearsDropsToTrialRightAway() {
        Subscription s = overdue(SubscriptionStatus.PAST_DUE, DUE);

        service().cancelSubscription();

        assertEquals(SubscriptionStatus.CANCELLED, s.getStatus());
        assertEquals(PlanTier.TRIAL, account.getPlan(), "en mora no hay período pagado que conservar");
        assertNull(account.getPlanExpiresAt());
    }
}
