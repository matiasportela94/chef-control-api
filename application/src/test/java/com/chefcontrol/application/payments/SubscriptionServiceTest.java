package com.chefcontrol.application.payments;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.payments.PaymentProvider.CreatedPayment;
import com.chefcontrol.application.payments.PaymentProvider.CreatedSubscription;
import com.chefcontrol.application.payments.PaymentProvider.ProviderPayment;
import com.chefcontrol.application.payments.PaymentProvider.ProviderSubscription;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.application.port.EmailService;
import com.chefcontrol.domain.account.Account;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.payments.BillingPeriod;
import com.chefcontrol.domain.payments.ChangeRequestStatus;
import com.chefcontrol.domain.payments.OneTimePayment;
import com.chefcontrol.domain.payments.OneTimePaymentRepository;
import com.chefcontrol.domain.payments.OneTimePaymentStatus;
import com.chefcontrol.domain.payments.SubscriptionChangeRequest;
import com.chefcontrol.domain.payments.SubscriptionChangeRequestRepository;
import com.chefcontrol.domain.payments.SubscriptionChangeType;
import com.chefcontrol.domain.payments.PaymentWebhookEvent;
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
import java.time.Duration;
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
    private final FakeChangeRequests changes = new FakeChangeRequests();
    private final FakeOneTimePayments payments = new FakeOneTimePayments();
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
        public Optional<Subscription> findById(UUID id) {
            return all.stream().filter(s -> s.getId().equals(id)).findFirst();
        }
        public Optional<Subscription> findLatestCurrentByAccountId(UUID accountId) {
            return all.stream().filter(s -> s.getAccountId().equals(accountId)
                    && (s.getStartsAt() == null || s.getCurrentPeriodEnd() != null)).reduce((a, b) -> b);
        }
        public List<Subscription> findOverdue(Instant now) {
            return all.stream().filter(s -> s.getStatus() == SubscriptionStatus.PAST_DUE
                    || (s.getStatus() == SubscriptionStatus.ACTIVE && !s.getCurrentPeriodEnd().isAfter(now))
                    || ((s.getStatus() == SubscriptionStatus.PENDING || s.getStatus() == SubscriptionStatus.SCHEDULED)
                        && s.getStartsAt() != null && !s.getStartsAt().isAfter(now))).toList();
        }
    }

    static class FakeChangeRequests implements SubscriptionChangeRequestRepository {
        final List<SubscriptionChangeRequest> all = new ArrayList<>();

        public SubscriptionChangeRequest save(SubscriptionChangeRequest r) {
            if (r.getId() == null) {
                assertTrue(findPendingByAccountId(r.getAccountId()).isEmpty(), "una sola PENDING por cuenta (índice de V31)");
                r.setId(UUID.randomUUID());
                all.add(r);
            }
            return r;
        }
        public Optional<SubscriptionChangeRequest> findPendingByAccountId(UUID accountId) {
            return all.stream().filter(r -> r.getAccountId().equals(accountId)
                    && r.getStatus() == ChangeRequestStatus.PENDING).findFirst();
        }
    }

    static class FakeOneTimePayments implements OneTimePaymentRepository {
        final List<OneTimePayment> all = new ArrayList<>();

        public OneTimePayment save(OneTimePayment p) {
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
                all.add(p);
            }
            return p;
        }
        public Optional<OneTimePayment> findById(UUID id) {
            return all.stream().filter(p -> p.getId().equals(id)).findFirst();
        }
        public Optional<OneTimePayment> findLatestPendingByAccountIdAndPurpose(UUID accountId, String purpose) {
            return all.stream().filter(p -> p.getAccountId().equals(accountId) && p.getPurpose().equals(purpose)
                    && p.getStatus() == OneTimePaymentStatus.PENDING).reduce((a, b) -> b);
        }
    }

    /** "Ahora" de los tests: 20/08/2026 09:00 hora argentina. */
    private final Instant now = Instant.parse("2026-08-20T12:00:00Z");

    private SubscriptionService service() {
        return service(now);
    }

    private SubscriptionService service(Instant at) {
        return new SubscriptionService(subscriptions, changes, payments, provider, accounts, restaurants, users,
                currentUser, audit, email, Clock.fixed(at, ZoneOffset.UTC),
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

    @Test
    void aChargeStillBeingProcessedIsNotArrears() {
        // MP cobra hasta una hora después de la fecha: a la hora del vencimiento todavía no es mora.
        Subscription s = overdue(SubscriptionStatus.ACTIVE, now.minus(Duration.ofHours(1)));

        service().collectOverdueSubscription(s);

        assertEquals(SubscriptionStatus.ACTIVE, s.getStatus());
        assertNull(account.getPlanExpiresAt());
        verifyNoInteractions(email);
    }

    // ---- cambio de plan

    /** Fin del período vigente: 04/09 17:00 hora AR. Arrancó el 04/08: 31 días, y hoy (20/08) quedan 15. */
    private static final Instant PERIOD_END = Instant.parse("2026-09-04T20:00:00Z");

    private Subscription active(PlanTier tier, BillingPeriod period, String amountArs) {
        Subscription s = existing(SubscriptionStatus.ACTIVE, "mp-1");
        s.setPlanTier(tier);
        s.setBillingPeriod(period);
        s.setAmountArs(new BigDecimal(amountArs));
        s.setCurrentPeriodEnd(PERIOD_END);
        account.setPlan(tier);
        return s;
    }

    private void paymentNotice(String providerPaymentId) {
        new OneTimePaymentWebhookHandler(provider, payments, service()).handle(new PaymentWebhookEvent(
                "MERCADOPAGO", "evt-" + providerPaymentId, "payment", providerPaymentId, "{}"));
    }

    private OneTimePayment requestUpgradeCheckout(PlanTier to) {
        when(provider.createPayment(any())).thenReturn(new CreatedPayment("pref-1", "https://mp/pay"));
        service().requestSubscriptionChange(to, BillingPeriod.MONTHLY);
        return payments.all.get(payments.all.size() - 1);
    }

    private void approved(String providerPaymentId, OneTimePayment payment, BigDecimal amount) {
        when(provider.getPayment(providerPaymentId)).thenReturn(new ProviderPayment(providerPaymentId, true,
                payment.getId().toString(), amount, "ARS"));
    }

    @Test
    void upgradePreviewProratesTheDifferenceByCalendarDaysRoundedToThePeso() {
        active(PlanTier.STARTER, BillingPeriod.MONTHLY, "100000");

        var p = service().previewSubscriptionChange(PlanTier.PRO, BillingPeriod.MONTHLY);

        assertEquals(SubscriptionChangeType.UPGRADE, p.changeType());
        assertEquals(15, p.daysRemaining());
        assertEquals(31, p.periodDays());
        assertEquals(new BigDecimal("33871"), p.chargeNowArs(), "70.000 × 15 / 31 = 33.870,97");
        assertEquals(now, p.effectiveAt());
        assertEquals(PERIOD_END, p.nextChargeAt());
        assertEquals(new BigDecimal("170000"), p.newAmountArs());
        assertTrue(p.requiresCheckout());
        assertFalse(p.replacesPendingChange());
    }

    @Test
    void upgradeOpensACheckoutAndChangesThePlanOnlyWhenThePaymentIsApproved() {
        Subscription s = active(PlanTier.STARTER, BillingPeriod.MONTHLY, "100000");
        when(provider.createPayment(any())).thenReturn(new CreatedPayment("pref-1", "https://mp/pay"));

        var result = service().requestSubscriptionChange(PlanTier.PRO, BillingPeriod.MONTHLY);

        assertEquals("https://mp/pay", result.checkoutUrl());
        assertNull(result.pendingChange());
        OneTimePayment payment = payments.all.get(0);
        assertEquals(new BigDecimal("33871"), payment.getAmountArs());
        verify(provider).createPayment(argThat(r -> r.externalReference().equals(payment.getId().toString())
                && r.amountArs().equals(new BigDecimal("33871"))
                && r.expiresAt().equals(now.plus(SubscriptionService.UPGRADE_CHECKOUT_TTL))));
        assertEquals(PlanTier.STARTER, account.getPlan(), "sin pago no hay suba");

        approved("pay-1", payment, new BigDecimal("33871"));
        paymentNotice("pay-1");
        paymentNotice("pay-1"); // aviso repetido: se aplica una sola vez

        assertEquals(OneTimePaymentStatus.APPROVED, payment.getStatus());
        assertEquals(PlanTier.PRO, s.getPlanTier());
        assertEquals(new BigDecimal("170000"), s.getAmountArs());
        assertEquals(PlanTier.PRO, account.getPlan());
        verify(provider, times(1)).updateSubscriptionAmount("mp-1", new BigDecimal("170000"));
        verify(email, times(1)).sendPlanChanged("dueno@resto.com", "Ana", "Pro", new BigDecimal("170000"),
                BillingPeriod.MONTHLY, "https://app.test/dashboard");
    }

    @Test
    void anUpgradePaymentForADifferentAmountIsNotApplied() {
        Subscription s = active(PlanTier.STARTER, BillingPeriod.MONTHLY, "100000");
        OneTimePayment payment = requestUpgradeCheckout(PlanTier.PRO);
        approved("pay-1", payment, new BigDecimal("1"));

        paymentNotice("pay-1");

        assertEquals(PlanTier.STARTER, s.getPlanTier());
        assertEquals(OneTimePaymentStatus.PENDING, payment.getStatus());
    }

    @Test
    void aSecondUpgradeCheckoutReplacesTheFirstButAPaymentOfTheFirstStillCounts() {
        Subscription s = active(PlanTier.STARTER, BillingPeriod.MONTHLY, "100000");
        OneTimePayment first = requestUpgradeCheckout(PlanTier.PRO);
        requestUpgradeCheckout(PlanTier.PRO);
        assertEquals(OneTimePaymentStatus.CANCELLED, first.getStatus());

        approved("pay-1", first, new BigDecimal("33871"));
        paymentNotice("pay-1");

        assertEquals(PlanTier.PRO, s.getPlanTier());
    }

    @Test
    void downgradeLowersTheAmountNowAndAppliesWhenTheNextChargeEnters() {
        Subscription s = active(PlanTier.PRO, BillingPeriod.MONTHLY, "170000");

        var result = service().requestSubscriptionChange(PlanTier.STARTER, BillingPeriod.MONTHLY);

        assertEquals(SubscriptionChangeType.DOWNGRADE, result.changeType());
        assertNull(result.checkoutUrl());
        assertEquals(PERIOD_END, result.pendingChange().request().getEffectiveAt());
        verify(provider).updateSubscriptionAmount("mp-1", new BigDecimal("100000"));
        assertEquals(PlanTier.PRO, account.getPlan(), "rige al fin del período");
        assertEquals(SubscriptionChangeType.DOWNGRADE,
                service().getSubscription().orElseThrow().pendingChange().request().getChangeType());

        when(provider.getSubscription("mp-1")).thenReturn(new ProviderSubscription("mp-1", SubscriptionStatus.ACTIVE,
                Instant.parse("2026-10-04T20:00:00Z")));
        service(PERIOD_END.plus(Duration.ofMinutes(10))).syncSubscription("mp-1");

        assertEquals(PlanTier.STARTER, s.getPlanTier());
        assertEquals(new BigDecimal("100000"), s.getAmountArs());
        assertEquals(PlanTier.STARTER, account.getPlan());
        assertEquals(ChangeRequestStatus.APPLIED, changes.all.get(0).getStatus());
        verify(email).sendPlanChanged("dueno@resto.com", "Ana", "Starter", new BigDecimal("100000"),
                BillingPeriod.MONTHLY, "https://app.test/dashboard");
    }

    @Test
    void cancellingADowngradeRestoresTheAmountTheSubscriptionWasPayingNotTheListPrice() {
        active(PlanTier.PRO, BillingPeriod.MONTHLY, "150000"); // precio viejo
        service().requestSubscriptionChange(PlanTier.STARTER, BillingPeriod.MONTHLY);

        service().cancelSubscriptionChange();

        verify(provider).updateSubscriptionAmount("mp-1", new BigDecimal("150000"));
        assertEquals(ChangeRequestStatus.CANCELLED, changes.all.get(0).getStatus());
        assertNull(service().getSubscription().orElseThrow().pendingChange());
        assertEquals(ErrorCode.SUBSCRIPTION_CHANGE_NOT_FOUND,
                codeOf(assertThrows(AppException.class, () -> service().cancelSubscriptionChange())));
    }

    @Test
    void aChangeThatReachedItsDateCannotBeCancelledOrReplaced() {
        active(PlanTier.PRO, BillingPeriod.MONTHLY, "170000");
        service().requestSubscriptionChange(PlanTier.STARTER, BillingPeriod.MONTHLY);

        var later = service(PERIOD_END.plus(Duration.ofMinutes(10)));
        assertEquals(ErrorCode.SUBSCRIPTION_CHANGE_IN_PROGRESS,
                codeOf(assertThrows(AppException.class, later::cancelSubscriptionChange)));
        assertEquals(ErrorCode.SUBSCRIPTION_CHANGE_IN_PROGRESS, codeOf(assertThrows(AppException.class,
                () -> later.requestSubscriptionChange(PlanTier.PRO, BillingPeriod.ANNUAL))));
    }

    @Test
    void changesAreRejectedWithTheirOwnCodes() {
        Subscription s = active(PlanTier.PRO, BillingPeriod.MONTHLY, "170000");
        var service = service();
        assertEquals(ErrorCode.SUBSCRIPTION_CHANGE_SAME_PLAN, codeOf(assertThrows(AppException.class,
                () -> service.requestSubscriptionChange(PlanTier.PRO, BillingPeriod.MONTHLY))));
        assertEquals(ErrorCode.SUBSCRIPTION_UPGRADE_AND_PERIOD_CHANGE, codeOf(assertThrows(AppException.class,
                () -> service.previewSubscriptionChange(PlanTier.ENTERPRISE, BillingPeriod.ANNUAL))));
        assertEquals(ErrorCode.INVALID_SUBSCRIPTION_PLAN, codeOf(assertThrows(AppException.class,
                () -> service.requestSubscriptionChange(PlanTier.TRIAL, BillingPeriod.MONTHLY))));

        s.setStatus(SubscriptionStatus.PAST_DUE);
        assertEquals(ErrorCode.SUBSCRIPTION_PAST_DUE, codeOf(assertThrows(AppException.class,
                () -> service.requestSubscriptionChange(PlanTier.STARTER, BillingPeriod.MONTHLY))));

        s.setStatus(SubscriptionStatus.CANCELLED);
        assertEquals(ErrorCode.SUBSCRIPTION_NOT_ACTIVE, codeOf(assertThrows(AppException.class,
                () -> service.previewSubscriptionChange(PlanTier.STARTER, BillingPeriod.MONTHLY))));

        s.setStatus(SubscriptionStatus.ACTIVE);
        when(currentUser.currentUserId()).thenReturn(UUID.randomUUID());
        assertEquals(ErrorCode.NOT_ACCOUNT_OWNER, codeOf(assertThrows(AppException.class,
                () -> service.previewSubscriptionChange(PlanTier.STARTER, BillingPeriod.MONTHLY))));
        verify(provider, never()).updateSubscriptionAmount(anyString(), any());
        verify(provider, never()).createPayment(any());
    }

    // ---- cambio de período

    /** Pide pasar a anual y, si {@code authorize}, el cliente lo autoriza en MP (la nueva queda SCHEDULED). */
    private Subscription periodChange(PlanTier from, String fromAmount, PlanTier to, boolean authorize) {
        active(from, BillingPeriod.MONTHLY, fromAmount);
        when(provider.createSubscription(argThat(r -> r != null && r.startDate() != null)))
                .thenReturn(new CreatedSubscription("mp-2", "https://mp/checkout-2"));
        var result = service().requestSubscriptionChange(to, BillingPeriod.ANNUAL);
        assertEquals("https://mp/checkout-2", result.checkoutUrl());
        Subscription replacement = subscriptions.findByProviderSubscriptionId("MERCADOPAGO", "mp-2").orElseThrow();
        if (authorize) {
            when(provider.getSubscription("mp-2")).thenReturn(new ProviderSubscription("mp-2", SubscriptionStatus.ACTIVE, PERIOD_END));
            service().syncSubscription("mp-2");
        }
        return replacement;
    }

    private Subscription old() {
        return subscriptions.findByProviderSubscriptionId("MERCADOPAGO", "mp-1").orElseThrow();
    }

    @Test
    void periodChangeIsANewSubscriptionStartingAtThePeriodEndWithItsOwnCheckout() {
        Subscription replacement = periodChange(PlanTier.STARTER, "100000", PlanTier.STARTER, false);

        verify(provider).createSubscription(argThat(r -> r != null && PERIOD_END.equals(r.startDate())
                && r.frequencyMonths() == 12 && r.amountArs().equals(new BigDecimal("1020000"))));
        var details = service().getSubscription().orElseThrow();
        assertEquals("mp-1", details.subscription().getProviderSubscriptionId(), "la nueva no se muestra hasta arrancar");
        assertTrue(details.pendingChange().awaitingCheckout());
        assertEquals(SubscriptionChangeType.PERIOD_CHANGE, details.pendingChange().request().getChangeType());

        when(provider.getSubscription("mp-2")).thenReturn(new ProviderSubscription("mp-2", SubscriptionStatus.ACTIVE, PERIOD_END));
        service().syncSubscription("mp-2");

        assertEquals(SubscriptionStatus.SCHEDULED, replacement.getStatus());
        verify(provider).setSubscriptionEndDate("mp-1", PERIOD_END.minus(Duration.ofDays(1)));
        assertFalse(service().getSubscription().orElseThrow().pendingChange().awaitingCheckout());
        verify(provider, never()).updateSubscriptionAmount(anyString(), any());
    }

    @Test
    void betweenTheOldPeriodEndAndTheFirstChargeOfTheNewOneNothingFallsIntoArrears() {
        Subscription replacement = periodChange(PlanTier.STARTER, "100000", PlanTier.STARTER, true);
        when(provider.getSubscription("mp-1")).thenReturn(new ProviderSubscription("mp-1", SubscriptionStatus.ACTIVE, PERIOD_END));
        var inTheWindow = service(PERIOD_END.plus(Duration.ofMinutes(30)));

        for (Subscription s : inTheWindow.findOverdueSubscriptions()) inTheWindow.collectOverdueSubscription(s);

        assertEquals(SubscriptionStatus.ACTIVE, old().getStatus());
        assertEquals(SubscriptionStatus.SCHEDULED, replacement.getStatus());
        assertNull(account.getPlanExpiresAt());
        assertEquals(PlanTier.STARTER, account.effectivePlan(PERIOD_END.plus(Duration.ofMinutes(30))));
        verifyNoInteractions(email);
    }

    @Test
    void theFirstChargeOfTheNewSubscriptionClosesTheOldOneAndAppliesTheChange() {
        Subscription replacement = periodChange(PlanTier.PRO, "170000", PlanTier.STARTER, true);
        Instant nextYear = Instant.parse("2027-09-04T20:00:00Z");
        when(provider.getSubscription("mp-2")).thenReturn(new ProviderSubscription("mp-2", SubscriptionStatus.ACTIVE, nextYear));

        service(PERIOD_END.plus(Duration.ofMinutes(40))).syncSubscription("mp-2");

        assertEquals(SubscriptionStatus.CANCELLED, old().getStatus());
        verify(provider).cancelSubscription("mp-1"); // por si el end_date no alcanzó
        assertEquals(SubscriptionStatus.ACTIVE, replacement.getStatus());
        assertEquals(nextYear, replacement.getCurrentPeriodEnd());
        assertEquals(PlanTier.STARTER, account.getPlan());
        assertNull(account.getPlanExpiresAt());
        assertEquals(ChangeRequestStatus.APPLIED, changes.all.get(0).getStatus());
        assertEquals("mp-2", service().getSubscription().orElseThrow().subscription().getProviderSubscriptionId());
        verify(email).sendPlanChanged("dueno@resto.com", "Ana", "Starter", new BigDecimal("1020000"),
                BillingPeriod.ANNUAL, "https://app.test/dashboard");
        verify(email, never()).sendSubscriptionWelcome(any(), any(), any(), any(), any(), any());
    }

    @Test
    void ifTheFirstChargeOfTheNewSubscriptionFailsTheArrearsRunOnItFromTheOldPeriodEnd() {
        // baja + período: la cuenta tiene todavía PRO, pero la que no pagó es la nueva (STARTER anual)
        Subscription replacement = periodChange(PlanTier.PRO, "170000", PlanTier.STARTER, true);
        when(provider.getSubscription("mp-1")).thenReturn(new ProviderSubscription("mp-1", SubscriptionStatus.ACTIVE, PERIOD_END));
        var job = service(PERIOD_END.plus(Duration.ofHours(3)));

        for (Subscription s : job.findOverdueSubscriptions()) job.collectOverdueSubscription(s);

        Instant accessUntil = PERIOD_END.plus(SubscriptionService.GRACE);
        assertEquals(SubscriptionStatus.CANCELLED, old().getStatus());
        assertEquals(SubscriptionStatus.PAST_DUE, replacement.getStatus());
        assertEquals(PERIOD_END, replacement.getCurrentPeriodEnd());
        assertEquals(accessUntil, account.getPlanExpiresAt());
        assertEquals(PlanTier.TRIAL, account.effectivePlan(accessUntil));
        verify(email).sendPaymentFailedReminder("dueno@resto.com", "Ana", "Starter", accessUntil, "https://app.test/plan");
        assertEquals("mp-2", service().getSubscription().orElseThrow().subscription().getProviderSubscriptionId());
    }

    @Test
    void aPeriodChangeNeverAuthorizedIsDiscardedWhenTheOldSubscriptionRenews() {
        Subscription replacement = periodChange(PlanTier.STARTER, "100000", PlanTier.STARTER, false);
        when(provider.getSubscription("mp-1")).thenReturn(new ProviderSubscription("mp-1", SubscriptionStatus.ACTIVE,
                Instant.parse("2026-10-04T20:00:00Z")));

        service(PERIOD_END.plus(Duration.ofMinutes(10))).syncSubscription("mp-1");

        verify(provider).cancelSubscription("mp-2");
        assertEquals(SubscriptionStatus.CANCELLED, replacement.getStatus());
        assertEquals(ChangeRequestStatus.CANCELLED, changes.all.get(0).getStatus());
    }

    @Test
    void replacingAConfirmedPeriodChangeWithADowngradeCancelsTheNewOneAndLiftsTheEndDate() {
        Subscription replacement = periodChange(PlanTier.PRO, "170000", PlanTier.PRO, true);

        service().requestSubscriptionChange(PlanTier.STARTER, BillingPeriod.MONTHLY);

        verify(provider).cancelSubscription("mp-2");
        assertEquals(SubscriptionStatus.CANCELLED, replacement.getStatus());
        verify(provider).setSubscriptionEndDate("mp-1", null);
        verify(provider).updateSubscriptionAmount("mp-1", new BigDecimal("100000"));
        assertEquals(SubscriptionChangeType.DOWNGRADE,
                changes.findPendingByAccountId(accountId).orElseThrow().getChangeType());
    }

    @Test
    void cancellingTheSubscriptionOrClosingTheAccountAlsoCancelsTheNewOne() {
        Subscription replacement = periodChange(PlanTier.STARTER, "100000", PlanTier.STARTER, true);

        service().cancelBeforeAccountClosure(accountId);
        verify(provider).cancelSubscription("mp-1");
        verify(provider).cancelSubscription("mp-2");

        service().cancelSubscription();
        assertEquals(SubscriptionStatus.CANCELLED, replacement.getStatus());
        assertTrue(changes.findPendingByAccountId(accountId).isEmpty());
    }

    @Test
    void anUpgradePaidWithAPendingDowngradeDiscardsTheDowngrade() {
        Subscription s = active(PlanTier.PRO, BillingPeriod.MONTHLY, "170000");
        service().requestSubscriptionChange(PlanTier.STARTER, BillingPeriod.MONTHLY);

        assertTrue(service().previewSubscriptionChange(PlanTier.ENTERPRISE, BillingPeriod.MONTHLY).replacesPendingChange());
        OneTimePayment payment = requestUpgradeCheckout(PlanTier.ENTERPRISE);
        assertEquals(ChangeRequestStatus.PENDING, changes.all.get(0).getStatus(), "se descarta al pagar, no al pedir");

        approved("pay-1", payment, payment.getAmountArs());
        paymentNotice("pay-1");

        assertEquals(ChangeRequestStatus.CANCELLED, changes.all.get(0).getStatus());
        assertEquals(PlanTier.ENTERPRISE, s.getPlanTier());
        verify(provider).updateSubscriptionAmount("mp-1", new BigDecimal("225000"));
    }
}
