package com.chefcontrol.application.payments;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.payments.PaymentProvider.CreatedPayment;
import com.chefcontrol.application.payments.PaymentProvider.CreatedSubscription;
import com.chefcontrol.application.payments.PaymentProvider.NewPayment;
import com.chefcontrol.application.payments.PaymentProvider.NewSubscription;
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
import com.chefcontrol.domain.payments.Subscription;
import com.chefcontrol.domain.payments.SubscriptionChangeRequest;
import com.chefcontrol.domain.payments.SubscriptionChangeRequestRepository;
import com.chefcontrol.domain.payments.SubscriptionChangeType;
import com.chefcontrol.domain.payments.SubscriptionRepository;
import com.chefcontrol.domain.payments.SubscriptionStatus;
import com.chefcontrol.domain.plan.PlanTier;
import com.chefcontrol.domain.repository.AccountRepository;
import com.chefcontrol.domain.repository.RestaurantRepository;
import com.chefcontrol.domain.repository.UserRepository;
import com.chefcontrol.domain.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Cobro del plan de una cuenta. Es la pieza de la capa de pagos que conoce a la app anfitriona
 * ({@link Account} y {@link PlanTier}): el proveedor, el webhook y el registro de avisos no.
 * Si los pagos se extraen a otro repo, esta clase es la que cada proyecto reescribe.
 *
 * <p>La verdad del estado la tiene el proveedor. Este servicio nunca "aplica" un evento: ante un
 * aviso consulta el estado actual y lo copia ({@link #syncSubscription}), así que un aviso repetido
 * o fuera de orden no puede dejar un estado equivocado.
 *
 * <p>Quien pagó conserva el plan hasta que venza lo pagado, aunque cancele: ver {@link #applyStatus}.
 * Quien no paga entra en mora con {@link #GRACE} de gracia: ver {@link #collectOverdueSubscription}.
 *
 * <p>Cambio de plan (Mati, 2026-10-23 y 2026-11-16): la suba es inmediata con un pago único por la diferencia
 * prorrateada ({@link #applyPaidUpgrade}); la baja y el cambio de período son una solicitud que rige al terminar el
 * período ({@link SubscriptionChangeRequest}) y se aplica o se descarta al releer la suscripción ({@link #reconcile}).
 */
@Slf4j
@Service
public class SubscriptionService {

    public record Checkout(Subscription subscription, String checkoutUrl) {}

    /** @param awaitingCheckout cambio de período cuya suscripción nueva el cliente todavía no autorizó en el proveedor */
    public record PendingChange(SubscriptionChangeRequest request, boolean awaitingCheckout) {}

    public record SubscriptionDetails(Subscription subscription, PendingChange pendingChange) {}

    /**
     * @param chargeNowArs  solo en una suba: lo que se paga hoy
     * @param effectiveAt   suba: ahora (al aprobarse el pago); el resto: el fin del período
     * @param nextChargeAt  el primer cobro con {@code newAmountArs}
     */
    public record ChangePreview(SubscriptionChangeType changeType, PlanTier planTier, BillingPeriod billingPeriod,
                                boolean requiresCheckout, BigDecimal chargeNowArs, int daysRemaining, int periodDays,
                                Instant effectiveAt, Instant nextChargeAt, BigDecimal newAmountArs,
                                boolean replacesPendingChange) {}

    /** @param checkoutUrl suba y cambio de período; null en una baja */
    public record ChangeResult(SubscriptionChangeType changeType, String checkoutUrl, PendingChange pendingChange) {}

    /**
     * Cuánto conserva el plan una cuenta en mora después de que terminó lo pagado (decidido por Mati,
     * 2026-10-12): "se intenta el día que vence, tres días seguidos, y al tercero baja a TRIAL".
     * Vence el 15/09 a las 17:00 → plan hasta el 18/09 a las 17:00.
     */
    static final Duration GRACE = Duration.ofDays(3);

    /**
     * MP cobra hasta una hora después de la fecha de cobro (su doc). Antes de esto un cobro que no entró todavía no es
     * mora: sin el margen, la tarea de las 09:00 marcaba en mora (y mandaba el recordatorio) a quien estaba pagando.
     */
    static final Duration CHARGE_DELAY = Duration.ofHours(2);

    /**
     * Cuánto tiene que correrse el próximo cobro para contar como "el período se cobró". El siguiente está a un mes o
     * un año; el margen evita leer como cobro un ajuste de minutos del proveedor.
     */
    private static final Duration PERIOD_ADVANCE_MARGIN = Duration.ofDays(1);

    /** Vigencia del checkout de una suba: después se pide otro, con el prorrateo de ese día. */
    static final Duration UPGRADE_CHECKOUT_TTL = Duration.ofMinutes(30);

    private static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionChangeRequestRepository changeRequestRepository;
    private final OneTimePaymentRepository oneTimePaymentRepository;
    private final PaymentProvider paymentProvider;
    private final AccountRepository accountRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final EmailService emailService;
    private final Clock clock;
    private final Map<PlanTier, BigDecimal> monthlyPrices = new EnumMap<>(PlanTier.class);
    private final BigDecimal annualDiscountPercent;
    private final String frontendUrl;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               SubscriptionChangeRequestRepository changeRequestRepository,
                               OneTimePaymentRepository oneTimePaymentRepository,
                               PaymentProvider paymentProvider,
                               AccountRepository accountRepository,
                               RestaurantRepository restaurantRepository,
                               UserRepository userRepository,
                               CurrentUserProvider currentUserProvider,
                               AuditService auditService,
                               EmailService emailService,
                               Clock clock,
                               @Value("${app.payments.plans.starter-amount-ars:100000}") BigDecimal starterAmountArs,
                               @Value("${app.payments.plans.pro-amount-ars:170000}") BigDecimal proAmountArs,
                               @Value("${app.payments.plans.enterprise-amount-ars:225000}") BigDecimal enterpriseAmountArs,
                               @Value("${app.payments.plans.annual-discount-percent:15}") BigDecimal annualDiscountPercent,
                               @Value("${app.frontend.url:http://localhost:4200}") String frontendUrl) {
        this.subscriptionRepository = subscriptionRepository;
        this.changeRequestRepository = changeRequestRepository;
        this.oneTimePaymentRepository = oneTimePaymentRepository;
        this.paymentProvider = paymentProvider;
        this.accountRepository = accountRepository;
        this.restaurantRepository = restaurantRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.auditService = auditService;
        this.emailService = emailService;
        this.clock = clock;
        this.monthlyPrices.put(PlanTier.STARTER, starterAmountArs);
        this.monthlyPrices.put(PlanTier.PRO, proAmountArs);
        this.monthlyPrices.put(PlanTier.ENTERPRISE, enterpriseAmountArs);
        this.annualDiscountPercent = annualDiscountPercent;
        this.frontendUrl = frontendUrl;
    }

    /**
     * Lo que se cobra por ciclo, en ARS enteros, o null si el plan no se puede contratar (TRIAL).
     * El anual es 12 meses con el descuento configurado, cobrado de una vez.
     */
    public BigDecimal priceFor(PlanTier planTier, BillingPeriod billingPeriod) {
        BigDecimal monthly = monthlyPrices.get(planTier);
        if (monthly == null || billingPeriod == BillingPeriod.MONTHLY) return monthly;
        return monthly.multiply(BigDecimal.valueOf(billingPeriod.months()))
                .multiply(BigDecimal.valueOf(100).subtract(annualDiscountPercent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }

    /**
     * Arranca la suscripción de la cuenta al plan indicado y devuelve el enlace de checkout del
     * proveedor, donde el cliente carga su tarjeta. El plan de la cuenta NO cambia acá: cambia
     * cuando el proveedor avisa que la suscripción quedó autorizada.
     *
     * <p>Solo el dueño, igual que cerrar o renombrar la cuenta: es la unidad que se factura.
     * Una suscripción vigente sin terminar el checkout (PENDING) se reemplaza; una ya ACTIVE no.
     * Quien canceló y todavía tiene plan pagado puede volver a suscribirse: al activarse la nueva
     * se pisa el plan y desaparece el corte.
     */
    @Transactional
    public Checkout createSubscription(PlanTier planTier, BillingPeriod billingPeriod) {
        Account account = currentAccount();
        requireOwner(account);

        BigDecimal amount = priceFor(planTier, billingPeriod);
        if (amount == null) {
            throw AppException.badRequest(ErrorCode.INVALID_SUBSCRIPTION_PLAN,
                    "Plan " + planTier + " cannot be subscribed to");
        }
        User owner = userRepository.findById(account.getOwnerUserId())
                .orElseThrow(() -> new IllegalStateException("Account owner not found"));

        subscriptionRepository.findLiveByAccountId(account.getId()).ifPresent(live -> {
            if (live.getStatus() == SubscriptionStatus.ACTIVE) {
                throw AppException.conflict(ErrorCode.SUBSCRIPTION_ALREADY_ACTIVE,
                        "The account already has an active subscription");
            }
            dropPendingChange(account.getId(), live);
            cancelAtProvider(live);
            live.setStatus(SubscriptionStatus.CANCELLED);
            subscriptionRepository.save(live);
        });

        Checkout checkout = openAtProvider(subscriptionRepository.save(Subscription.builder()
                .accountId(account.getId())
                .planTier(planTier)
                .billingPeriod(billingPeriod)
                .status(SubscriptionStatus.PENDING)
                .provider(paymentProvider.name())
                .amountArs(amount)
                .payerEmail(owner.getEmail())
                .build()));

        auditService.log(AuditAction.SUBSCRIPTION_CREATED, "Subscription", checkout.subscription().getId(),
                Map.of("planTier", planTier.name(), "billingPeriod", billingPeriod.name(), "amountArs", amount));
        return checkout;
    }

    /**
     * Crea en el proveedor la suscripción de una fila PENDING recién guardada. Si el proveedor falla se relanza: la
     * transacción se revierte y la fila no queda huérfana.
     */
    private Checkout openAtProvider(Subscription pending) {
        CreatedSubscription created;
        try {
            created = paymentProvider.createSubscription(new NewSubscription(
                    pending.getId().toString(),
                    "Chef Control - Plan " + pending.getPlanTier().name()
                            + (pending.getBillingPeriod() == BillingPeriod.ANNUAL ? " (anual)" : ""),
                    pending.getAmountArs(), pending.getBillingPeriod().months(), pending.getPayerEmail(), planLink(),
                    pending.getStartsAt()));
        } catch (PaymentProviderException e) {
            log.error("[PAYMENTS] could not create subscription at provider: {}", e.getMessage(), e);
            throw providerUnavailable();
        }
        pending.setProviderSubscriptionId(created.providerSubscriptionId());
        return new Checkout(subscriptionRepository.save(pending), created.checkoutUrl());
    }

    /**
     * La que se cobra o se cobró, más el cambio programado si hay uno. La suscripción nueva de un cambio de período
     * no aparece hasta que arranca: hasta ahí se muestra como el cambio pendiente.
     */
    @Transactional(readOnly = true)
    public Optional<SubscriptionDetails> getSubscription() {
        UUID accountId = currentAccount().getId();
        return subscriptionRepository.findLatestCurrentByAccountId(accountId)
                .map(s -> new SubscriptionDetails(s, changeRequestRepository.findPendingByAccountId(accountId)
                        .map(this::pendingChange).orElse(null)));
    }

    private PendingChange pendingChange(SubscriptionChangeRequest request) {
        boolean awaitingCheckout = request.isPeriodChange() && subscriptionRepository.findById(request.getNewSubscriptionId())
                .map(s -> s.getStatus() == SubscriptionStatus.PENDING).orElse(false);
        return new PendingChange(request, awaitingCheckout);
    }

    /**
     * Cancela la suscripción vigente: deja de cobrarse, pero la cuenta conserva el plan que pagó hasta
     * que venza el período ya abonado ({@link #applyStatus}). Se lleva el cambio programado, si hay.
     */
    @Transactional
    public Subscription cancelSubscription() {
        Account account = currentAccount();
        requireOwner(account);

        Subscription live = subscriptionRepository.findLiveByAccountId(account.getId())
                .orElseThrow(() -> AppException.notFound(ErrorCode.SUBSCRIPTION_NOT_FOUND,
                        "The account has no active subscription"));

        dropPendingChange(account.getId(), live);
        cancelAtProvider(live);
        return applyStatus(live, SubscriptionStatus.CANCELLED, null);
    }

    // ---- cambio de plan

    /** Qué pasaría con el cambio pedido, sin hacer nada. Mismas validaciones que {@link #requestSubscriptionChange}. */
    @Transactional(readOnly = true)
    public ChangePreview previewSubscriptionChange(PlanTier planTier, BillingPeriod billingPeriod) {
        Account account = currentAccount();
        requireOwner(account);
        Subscription live = requireChangeable(account);
        SubscriptionChangeType type = changeTypeOf(live, planTier, billingPeriod);
        return preview(live, type, planTier, billingPeriod, pendingChangeNotDue(account).isPresent());
    }

    /**
     * Suba: devuelve el checkout del pago único; el plan cambia cuando el pago se aprueba ({@link #applyPaidUpgrade}).
     * Baja: cambia ya el monto de la suscripción en el proveedor (afecta el próximo cobro) y queda programada.
     * Cambio de período: una suscripción nueva que arranca al fin del período, con su checkout (MP no cambia la
     * frecuencia de una suscripción, y sin tarjeta tokenizada solo la autoriza el cliente en su checkout).
     * Un cambio programado anterior se reemplaza.
     *
     * <p>ponytail: deshacer lo anterior y pedir lo nuevo son llamadas sueltas al proveedor, sin compensación: si la
     * segunda falla, la transacción se revierte pero la primera ya pasó en MP. Ventana de segundos; si aparece, lo
     * primero es releer el monto de la suscripción en MP al aplicar la baja.
     */
    @Transactional
    public ChangeResult requestSubscriptionChange(PlanTier planTier, BillingPeriod billingPeriod) {
        Account account = currentAccount();
        requireOwner(account);
        Subscription live = requireChangeable(account);
        SubscriptionChangeType type = changeTypeOf(live, planTier, billingPeriod);
        Optional<SubscriptionChangeRequest> pending = pendingChangeNotDue(account);
        BigDecimal amount = priceFor(planTier, billingPeriod);

        if (type == SubscriptionChangeType.UPGRADE) {
            // Lo programado se descarta recién al aprobarse el pago: un checkout abandonado no lo borra.
            BigDecimal chargeNow = preview(live, type, planTier, billingPeriod, pending.isPresent()).chargeNowArs();
            String checkoutUrl = createUpgradeCheckout(account, live, planTier, chargeNow);
            Map<String, Object> payload = changePayload(type, planTier, billingPeriod, amount);
            payload.put("chargeNowArs", chargeNow);
            auditService.log(AuditAction.SUBSCRIPTION_CHANGE_REQUESTED, "Subscription", live.getId(), payload);
            return new ChangeResult(type, checkoutUrl, null);
        }

        pending.ifPresent(p -> discardChange(p, live, true));
        SubscriptionChangeRequest.SubscriptionChangeRequestBuilder request = SubscriptionChangeRequest.builder()
                .accountId(account.getId())
                .subscriptionId(live.getId())
                .changeType(type)
                .planTier(planTier)
                .billingPeriod(billingPeriod)
                .amountArs(amount)
                .effectiveAt(live.getCurrentPeriodEnd())
                .status(ChangeRequestStatus.PENDING);
        String checkoutUrl = null;
        if (type.isPeriodChange()) {
            Checkout replacement = openAtProvider(subscriptionRepository.save(Subscription.builder()
                    .accountId(account.getId())
                    .planTier(planTier)
                    .billingPeriod(billingPeriod)
                    .status(SubscriptionStatus.PENDING)
                    .provider(paymentProvider.name())
                    .amountArs(amount)
                    .payerEmail(live.getPayerEmail())
                    .startsAt(live.getCurrentPeriodEnd())
                    .build()));
            request.newSubscriptionId(replacement.subscription().getId());
            checkoutUrl = replacement.checkoutUrl();
        } else {
            updateAmountAtProvider(live, amount);
        }
        SubscriptionChangeRequest saved = changeRequestRepository.save(request.build());

        auditService.log(AuditAction.SUBSCRIPTION_CHANGE_REQUESTED, "Subscription", live.getId(),
                changePayload(type, planTier, billingPeriod, amount));
        return new ChangeResult(type, checkoutUrl, new PendingChange(saved, type.isPeriodChange()));
    }

    /** Cancela el cambio programado: la suscripción vigente vuelve a cobrarse como venía. */
    @Transactional
    public Subscription cancelSubscriptionChange() {
        Account account = currentAccount();
        requireOwner(account);
        SubscriptionChangeRequest pending = pendingChangeNotDue(account)
                .orElseThrow(() -> AppException.notFound(ErrorCode.SUBSCRIPTION_CHANGE_NOT_FOUND,
                        "The account has no pending subscription change"));
        Subscription live = subscriptionRepository.findById(pending.getSubscriptionId())
                .orElseThrow(() -> new IllegalStateException("Subscription of change request not found"));

        discardChange(pending, live, true);
        auditService.log(AuditAction.SUBSCRIPTION_CHANGE_CANCELLED, "Subscription", live.getId(),
                changePayload(pending.getChangeType(), pending.getPlanTier(), pending.getBillingPeriod(), pending.getAmountArs()));
        return live;
    }

    /**
     * Aplica una suba ya pagada (la llama el aviso del pago único). Si la suscripción ya no es la que se prorrateó
     * —se canceló, entró en mora, o ya tiene ese plan porque se pagaron dos checkouts— no se aplica y se loguea
     * para devolver el pago a mano.
     */
    @Transactional
    public void applyPaidUpgrade(OneTimePayment payment) {
        String[] target = payment.getTarget().split(":");
        Subscription subscription = subscriptionRepository.findById(UUID.fromString(target[0])).orElse(null);
        PlanTier planTier = PlanTier.valueOf(target[1]);
        if (subscription == null || subscription.getStatus() != SubscriptionStatus.ACTIVE
                || planTier.compareTo(subscription.getPlanTier()) <= 0) {
            log.error("[PAYMENTS] upgrade payment {} ({} ARS, provider payment {}) is approved but cannot be applied to "
                            + "subscription {}: refund it by hand", payment.getId(), payment.getAmountArs(),
                    payment.getProviderPaymentId(), target[0]);
            return;
        }

        BigDecimal amount = priceFor(planTier, subscription.getBillingPeriod());
        changeRequestRepository.findPendingByAccountId(subscription.getAccountId())
                .ifPresent(p -> discardChange(p, subscription, true));
        updateAmountAtProvider(subscription, amount);
        subscription.setPlanTier(planTier);
        subscription.setAmountArs(amount);
        Subscription saved = subscriptionRepository.save(subscription);

        Account account = accountOf(saved);
        account.setPlan(planTier);
        account.setPlanExpiresAt(null);
        accountRepository.save(account);

        Map<String, Object> payload = changePayload(SubscriptionChangeType.UPGRADE, planTier, saved.getBillingPeriod(), amount);
        payload.put("oneTimePaymentId", payment.getId().toString());
        auditService.log(AuditAction.SUBSCRIPTION_CHANGE_APPLIED, "Subscription", saved.getId(), payload);
        notifyPlanChanged(saved);
    }

    private String createUpgradeCheckout(Account account, Subscription live, PlanTier planTier, BigDecimal chargeNow) {
        // ponytail: el checkout anterior no se cierra en MP, vence solo (UPGRADE_CHECKOUT_TTL). Si igual se pagan los
        // dos, applyPaidUpgrade loguea el segundo para devolverlo a mano.
        oneTimePaymentRepository.findLatestPendingByAccountIdAndPurpose(account.getId(), OneTimePayment.PLAN_UPGRADE)
                .ifPresent(old -> {
                    old.setStatus(OneTimePaymentStatus.CANCELLED);
                    oneTimePaymentRepository.save(old);
                });
        Instant expiresAt = clock.instant().plus(UPGRADE_CHECKOUT_TTL);
        OneTimePayment payment = oneTimePaymentRepository.save(OneTimePayment.builder()
                .accountId(account.getId())
                .purpose(OneTimePayment.PLAN_UPGRADE)
                .amountArs(chargeNow)
                .status(OneTimePaymentStatus.PENDING)
                .target(live.getId() + ":" + planTier.name())
                .expiresAt(expiresAt)
                .build());

        CreatedPayment created;
        try {
            created = paymentProvider.createPayment(new NewPayment(payment.getId().toString(),
                    "Chef Control - Pasar al plan " + planName(planTier), chargeNow, live.getPayerEmail(),
                    planLink(), expiresAt));
        } catch (PaymentProviderException e) {
            log.error("[PAYMENTS] could not create upgrade checkout at provider: {}", e.getMessage(), e);
            throw providerUnavailable();
        }
        payment.setPreferenceId(created.providerPreferenceId());
        oneTimePaymentRepository.save(payment);
        return created.checkoutUrl();
    }

    /**
     * Prorrateo de la suba (Mati, 2026-11-16): días calendario que quedan del período sobre los del período,
     * redondeado al peso, sobre la diferencia entre el precio nuevo y lo que esta suscripción pagó. Mínimo un día:
     * el día del cobro también se usa.
     */
    private ChangePreview preview(Subscription live, SubscriptionChangeType type, PlanTier planTier,
                                  BillingPeriod billingPeriod, boolean replacesPendingChange) {
        BigDecimal newAmount = priceFor(planTier, billingPeriod);
        Instant periodEnd = live.getCurrentPeriodEnd();
        LocalDate end = periodEnd.atZone(AR).toLocalDate();
        LocalDate start = periodEnd.atZone(AR).minusMonths(live.getBillingPeriod().months()).toLocalDate();
        int periodDays = (int) ChronoUnit.DAYS.between(start, end);
        long left = ChronoUnit.DAYS.between(clock.instant().atZone(AR).toLocalDate(), end);
        int daysRemaining = (int) Math.max(1, Math.min(periodDays, left));

        boolean upgrade = type == SubscriptionChangeType.UPGRADE;
        BigDecimal chargeNow = upgrade
                ? newAmount.subtract(live.getAmountArs()).multiply(BigDecimal.valueOf(daysRemaining))
                        .divide(BigDecimal.valueOf(periodDays), 0, RoundingMode.HALF_UP)
                : null;
        return new ChangePreview(type, planTier, billingPeriod, type != SubscriptionChangeType.DOWNGRADE, chargeNow,
                daysRemaining, periodDays, upgrade ? clock.instant() : periodEnd, periodEnd, newAmount,
                replacesPendingChange);
    }

    /** Solo una ACTIVE se cambia: en mora primero se paga (Mati, 2026-11-16); sin suscripción se usa POST /subscription. */
    private Subscription requireChangeable(Account account) {
        Subscription live = subscriptionRepository.findLiveByAccountId(account.getId()).orElse(null);
        if (live != null && live.getStatus() == SubscriptionStatus.PAST_DUE) {
            throw AppException.conflict(ErrorCode.SUBSCRIPTION_PAST_DUE,
                    "The subscription is past due: pay it before changing plans");
        }
        if (live == null || live.getStatus() != SubscriptionStatus.ACTIVE) {
            throw AppException.conflict(ErrorCode.SUBSCRIPTION_NOT_ACTIVE, "The account has no active subscription");
        }
        return live;
    }

    private SubscriptionChangeType changeTypeOf(Subscription live, PlanTier planTier, BillingPeriod billingPeriod) {
        if (priceFor(planTier, billingPeriod) == null) {
            throw AppException.badRequest(ErrorCode.INVALID_SUBSCRIPTION_PLAN,
                    "Plan " + planTier + " cannot be subscribed to: cancel the subscription instead");
        }
        int cmp = planTier.compareTo(live.getPlanTier());
        boolean periodChange = billingPeriod != live.getBillingPeriod();
        if (cmp > 0) {
            if (periodChange) {
                throw AppException.badRequest(ErrorCode.SUBSCRIPTION_UPGRADE_AND_PERIOD_CHANGE,
                        "Upgrade the plan and change the billing period in two steps");
            }
            return SubscriptionChangeType.UPGRADE;
        }
        if (cmp < 0) {
            return periodChange ? SubscriptionChangeType.DOWNGRADE_AND_PERIOD_CHANGE : SubscriptionChangeType.DOWNGRADE;
        }
        if (!periodChange) {
            throw AppException.badRequest(ErrorCode.SUBSCRIPTION_CHANGE_SAME_PLAN,
                    "The subscription already has that plan and billing period");
        }
        return SubscriptionChangeType.PERIOD_CHANGE;
    }

    /** El cambio programado, si hay. Uno que ya llegó a su fecha se está cobrando: no se toca. */
    private Optional<SubscriptionChangeRequest> pendingChangeNotDue(Account account) {
        Optional<SubscriptionChangeRequest> pending = changeRequestRepository.findPendingByAccountId(account.getId());
        if (pending.isPresent() && !pending.get().getEffectiveAt().isAfter(clock.instant())) {
            throw AppException.conflict(ErrorCode.SUBSCRIPTION_CHANGE_IN_PROGRESS,
                    "The pending subscription change is being charged right now");
        }
        return pending;
    }

    /**
     * Deshace un cambio programado. {@code restoreLive}: devolverle a la vigente su monto ({@code amountArs}, lo que
     * venía pagando, no el precio de lista) y sacarle el {@code end_date}; false cuando la vigente se está cancelando.
     */
    private void discardChange(SubscriptionChangeRequest request, Subscription live, boolean restoreLive) {
        if (request.isPeriodChange()) {
            subscriptionRepository.findById(request.getNewSubscriptionId()).ifPresent(replacement -> {
                boolean confirmed = replacement.getStatus() == SubscriptionStatus.SCHEDULED;
                cancelAtProvider(replacement);
                replacement.setStatus(SubscriptionStatus.CANCELLED);
                subscriptionRepository.save(replacement);
                if (confirmed && restoreLive) setEndDateAtProvider(live, null);
            });
        } else if (restoreLive) {
            updateAmountAtProvider(live, live.getAmountArs());
        }
        request.setStatus(ChangeRequestStatus.CANCELLED);
        changeRequestRepository.save(request);
    }

    /** Lo programado se va con la vigente (cancelada o reemplazada). Si la vigente ya es la nueva, la cancela quien llama. */
    private void dropPendingChange(UUID accountId, Subscription live) {
        changeRequestRepository.findPendingByAccountId(accountId).ifPresent(p -> {
            if (live.getId().equals(p.getNewSubscriptionId())) {
                p.setStatus(ChangeRequestStatus.CANCELLED);
                changeRequestRepository.save(p);
            } else {
                discardChange(p, live, false);
            }
        });
    }

    // ---- avisos del proveedor y mora

    /**
     * Copia a la base local el estado que el proveedor tiene HOY de esta suscripción. Lo dispara el
     * webhook; si el proveedor no responde la excepción sube y el webhook contesta error para que
     * el proveedor reintente.
     */
    @Transactional
    public void syncSubscription(String providerSubscriptionId) {
        Optional<Subscription> local = subscriptionRepository
                .findByProviderSubscriptionId(paymentProvider.name(), providerSubscriptionId);
        if (local.isEmpty()) {
            // Puede ser de otro entorno que comparte la aplicación de MP: se acepta y se ignora.
            log.warn("[PAYMENTS] notification for unknown subscription {}, ignoring", providerSubscriptionId);
            return;
        }

        reconcile(local.get());
    }

    /**
     * Cancela en el proveedor la suscripción vigente —y la nueva de un cambio de período— antes de que se cierre la
     * cuenta. Si no se pudiera, aborta: borrar la cuenta sin cancelarla dejaría un cobro mensual a nombre de
     * alguien que ya no existe. Las filas locales se van solas con la cascada de la cuenta.
     */
    @Transactional
    public void cancelBeforeAccountClosure(UUID accountId) {
        subscriptionRepository.findLiveByAccountId(accountId).ifPresent(this::cancelAtProvider);
        changeRequestRepository.findPendingByAccountId(accountId)
                .filter(SubscriptionChangeRequest::isPeriodChange)
                .flatMap(p -> subscriptionRepository.findById(p.getNewSubscriptionId()))
                .ifPresent(this::cancelAtProvider);
    }

    /** Lo que recorre la tarea diaria de mora ({@link OverdueSubscriptionsJob}). */
    @Transactional(readOnly = true)
    public List<Subscription> findOverdueSubscriptions() {
        return subscriptionRepository.findOverdue(clock.instant());
    }

    /**
     * Un paso de la tarea diaria de mora sobre una suscripción cuyo período pagado terminó (o la nueva de un cambio
     * de período cuyo arranque pasó). Primero relee el proveedor: si el cobro entró vuelve a ACTIVE y no pasa nada
     * más. Si sigue sin pagar, dentro de la gracia manda el recordatorio del día (corre una vez por día, así que son
     * hasta tres); pasada la gracia la cancela en el proveedor —para que no cobre después de haber bajado al
     * cliente—, baja la cuenta a TRIAL y le avisa.
     *
     * <p>El corte del acceso NO depende de esta tarea: lo hace {@code plan_expires_at} al segundo. Si el
     * proveedor no responde, lanza y la tarea lo reintenta al día siguiente.
     */
    @Transactional
    public void collectOverdueSubscription(Subscription overdue) {
        Subscription subscription = reconcile(overdue);
        if (subscription.getStatus() != SubscriptionStatus.PAST_DUE) return;

        Instant accessUntil = subscription.getCurrentPeriodEnd().plus(GRACE);
        User owner = ownerOf(subscription.getAccountId());
        String planName = planName(subscription.getPlanTier());
        if (clock.instant().isBefore(accessUntil)) {
            emailService.sendPaymentFailedReminder(owner.getEmail(), owner.getName(), planName, accessUntil, planLink());
            return;
        }
        cancelAtProvider(subscription);
        Subscription cancelled = applyStatus(subscription, SubscriptionStatus.CANCELLED, null);
        settlePendingChange(cancelled);
        emailService.sendPlanDowngradedToTrial(owner.getEmail(), owner.getName(), planName, planLink());
    }

    /** Relee la suscripción en el proveedor y copia el estado, detectando la mora y aplicando o tirando lo programado. */
    private Subscription reconcile(Subscription local) {
        ProviderSubscription remote = paymentProvider.getSubscription(local.getProviderSubscriptionId());
        if (remote.status() == null) {
            log.warn("[PAYMENTS] subscription {} is in a provider state we do not handle, no change",
                    local.getProviderSubscriptionId());
            return local;
        }
        if (isUnstartedReplacement(local)) return reconcileReplacement(local, remote);
        if (hasConfirmedReplacement(local)) {
            // La vieja de un cambio de período confirmado termina por end_date y la nueva cobra en su lugar: hasta que
            // la nueva arranque no entra en mora ni se baja (la cierra closeReplaced). La cuenta sigue con su plan.
            return local;
        }
        SubscriptionStatus target = remote.status() == SubscriptionStatus.ACTIVE && isUnpaid(local, remote.nextPaymentDate())
                ? SubscriptionStatus.PAST_DUE : remote.status();
        Subscription saved = applyStatus(local, target, remote.nextPaymentDate());
        settlePendingChange(saved);
        return saved;
    }

    private static boolean isUnstartedReplacement(Subscription s) {
        return s.getStartsAt() != null
                && (s.getStatus() == SubscriptionStatus.PENDING || s.getStatus() == SubscriptionStatus.SCHEDULED);
    }

    private boolean hasConfirmedReplacement(Subscription s) {
        return changeRequestRepository.findPendingByAccountId(s.getAccountId())
                .filter(r -> r.isPeriodChange() && s.getId().equals(r.getSubscriptionId()))
                .flatMap(r -> subscriptionRepository.findById(r.getNewSubscriptionId()))
                .map(n -> n.getStatus() == SubscriptionStatus.SCHEDULED)
                .orElse(false);
    }

    /**
     * La suscripción nueva de un cambio de período, antes de su primer cobro:
     * <ul>
     *   <li>pendiente en el proveedor: checkout sin terminar; si llegó el fin del período, el cambio se descarta;</li>
     *   <li>autorizada por primera vez: SCHEDULED, y a la vieja se le pone fin para que no cobre el período nuevo;</li>
     *   <li>cobró (el próximo cobro se corrió): cierra la vieja y arranca, con el plan nuevo;</li>
     *   <li>no cobró pasado {@link #CHARGE_DELAY}: cierra la vieja y entra en mora desde el fin del período viejo,
     *       con la gracia de siempre — aunque la cuenta tenga todavía el plan viejo (baja + período);</li>
     *   <li>cancelada en el proveedor: el cambio se cae y la vieja sigue.</li>
     * </ul>
     */
    private Subscription reconcileReplacement(Subscription local, ProviderSubscription remote) {
        SubscriptionChangeRequest request = changeRequestRepository.findPendingByAccountId(local.getAccountId())
                .filter(r -> local.getId().equals(r.getNewSubscriptionId()))
                .orElse(null);
        if (request == null) {
            // El cambio se descartó y el aviso llegó tarde: que no cobre.
            if (remote.status() != SubscriptionStatus.CANCELLED) cancelAtProvider(local);
            local.setStatus(SubscriptionStatus.CANCELLED);
            return subscriptionRepository.save(local);
        }
        Subscription old = subscriptionRepository.findById(request.getSubscriptionId())
                .orElseThrow(() -> new IllegalStateException("Subscription of change request not found"));
        Instant now = clock.instant();

        switch (remote.status()) {
            case CANCELLED -> {
                boolean confirmed = local.getStatus() == SubscriptionStatus.SCHEDULED;
                local.setStatus(SubscriptionStatus.CANCELLED);
                Subscription saved = subscriptionRepository.save(local);
                request.setStatus(ChangeRequestStatus.CANCELLED);
                changeRequestRepository.save(request);
                if (confirmed && old.getStatus() == SubscriptionStatus.ACTIVE) setEndDateAtProvider(old, null);
                return saved;
            }
            case PENDING -> {
                if (!now.isBefore(local.getStartsAt())) discardChange(request, old, false);
                return local;
            }
            case ACTIVE -> {
                if (local.getStatus() == SubscriptionStatus.PENDING) return confirmReplacement(local, old, request);
                if (advancedPast(remote.nextPaymentDate(), local.getStartsAt())) {
                    closeReplaced(old);
                    Subscription saved = applyStatus(local, SubscriptionStatus.ACTIVE, remote.nextPaymentDate());
                    settlePendingChange(saved);
                    return saved;
                }
                // Sin fecha no se presume mora (fail-open, como en isUnpaid): queda programada y se relee mañana.
                if (remote.nextPaymentDate() == null || now.isBefore(local.getStartsAt().plus(CHARGE_DELAY))) return local;
                closeReplaced(old);
                local.setCurrentPeriodEnd(local.getStartsAt());
                return applyStatus(local, SubscriptionStatus.PAST_DUE, remote.nextPaymentDate());
            }
            default -> {
                return local;
            }
        }
    }

    /**
     * El cliente autorizó la suscripción nueva: a la vieja se le pone fin un día antes de su próximo cobro, para que
     * no cobre el período que ahora paga la nueva (ponytail: un día y no minutos porque no sabemos si MP compara
     * fecha u hora; el acceso no depende de esto, lo maneja la nueva). Si ya no hay margen —autorizó con el cobro
     * encima— el cambio se descarta: es eso o cobrar dos veces.
     */
    private Subscription confirmReplacement(Subscription replacement, Subscription old, SubscriptionChangeRequest request) {
        Instant earliest = clock.instant().plus(Duration.ofMinutes(5));
        Instant endDate = replacement.getStartsAt().minus(Duration.ofDays(1));
        if (endDate.isBefore(earliest)) endDate = earliest;
        if (!endDate.isBefore(replacement.getStartsAt())) {
            log.warn("[PAYMENTS] replacement subscription {} authorized too close to the period end, discarding the change",
                    replacement.getId());
            discardChange(request, old, false);
            return replacement;
        }
        setEndDateAtProvider(old, endDate);
        replacement.setStatus(SubscriptionStatus.SCHEDULED);
        return subscriptionRepository.save(replacement);
    }

    /**
     * Cierra la vieja de un cambio de período cuando la nueva arranca (o entra en mora). Se cancela también en el
     * proveedor por si el {@code end_date} no alcanzó: sin eso, un doble cobro. No toca la cuenta: la maneja la nueva.
     */
    private void closeReplaced(Subscription old) {
        if (old.getStatus() == SubscriptionStatus.CANCELLED) return;
        cancelAtProvider(old);
        old.setStatus(SubscriptionStatus.CANCELLED);
        subscriptionRepository.save(old);
        auditService.log(AuditAction.SUBSCRIPTION_CANCELLED, "Subscription", old.getId(), auditPayload(old));
    }

    /** Aplica o descarta el cambio programado según cómo quedó la suscripción después de releerla. */
    private void settlePendingChange(Subscription subscription) {
        SubscriptionChangeRequest request = changeRequestRepository.findPendingByAccountId(subscription.getAccountId())
                .orElse(null);
        if (request == null) return;
        boolean isReplacement = subscription.getId().equals(request.getNewSubscriptionId());
        if (!isReplacement && !subscription.getId().equals(request.getSubscriptionId())) return;

        if (subscription.getStatus() == SubscriptionStatus.CANCELLED) {
            if (isReplacement) {
                request.setStatus(ChangeRequestStatus.CANCELLED);
                changeRequestRepository.save(request);
            } else {
                // Se terminó la vigente: lo programado no tiene sobre qué aplicarse.
                discardChange(request, subscription, false);
            }
            return;
        }
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) return;
        if (isReplacement) {
            markApplied(request, subscription); // la nueva cobró, también al salir de la mora
            return;
        }
        if (!advancedPast(subscription.getCurrentPeriodEnd(), request.getEffectiveAt())) return;
        if (request.isPeriodChange()) {
            // La vieja cobró el período nuevo: la nueva nunca se autorizó y el cambio se cae.
            discardChange(request, subscription, false);
            return;
        }
        // Baja: la vigente ya cobró el período nuevo con el monto bajado.
        PlanTier previousPlan = subscription.getPlanTier();
        subscription.setPlanTier(request.getPlanTier());
        subscription.setAmountArs(request.getAmountArs());
        Subscription saved = subscriptionRepository.save(subscription);
        Account account = accountOf(saved);
        if (account.getPlan() == previousPlan) {
            account.setPlan(request.getPlanTier());
            accountRepository.save(account);
        }
        markApplied(request, saved);
    }

    private void markApplied(SubscriptionChangeRequest request, Subscription subscription) {
        request.setStatus(ChangeRequestStatus.APPLIED);
        changeRequestRepository.save(request);
        auditService.log(AuditAction.SUBSCRIPTION_CHANGE_APPLIED, "Subscription", subscription.getId(),
                changePayload(request.getChangeType(), request.getPlanTier(), request.getBillingPeriod(), request.getAmountArs()));
        notifyPlanChanged(subscription);
    }

    /** El próximo cobro se corrió más allá de {@code mark}: el período que empezaba ahí ya se cobró. */
    private static boolean advancedPast(Instant nextPaymentDate, Instant mark) {
        return nextPaymentDate != null && nextPaymentDate.isAfter(mark.plus(PERIOD_ADVANCE_MARGIN));
    }

    /**
     * Autorizada en el proveedor pero sin cobrar: el próximo cobro que informa ya pasó (más {@link #CHARGE_DELAY}),
     * o sea que el de este período no entró. Solo para una suscripción que ya estuvo activa. Sin fecha no se presume
     * mora: un dato faltante no le corta el acceso a quien paga (y si de verdad no paga, MP la cancela
     * solo tras 3 cobros rechazados).
     */
    private boolean isUnpaid(Subscription local, Instant nextPaymentDate) {
        return (local.getStatus() == SubscriptionStatus.ACTIVE || local.getStatus() == SubscriptionStatus.PAST_DUE)
                && nextPaymentDate != null && !nextPaymentDate.plus(CHARGE_DELAY).isAfter(clock.instant());
    }

    /**
     * Lleva la suscripción local al estado {@code target} y ajusta el plan de la cuenta.
     *
     * <ul>
     *   <li>ACTIVE: la cuenta pasa al plan contratado, sin corte (también al salir de la mora). El fin de
     *       período es el próximo cobro que informa el proveedor; si no lo informó, una estimación (hoy + período).</li>
     *   <li>PAST_DUE: el plan se conserva durante la gracia: {@code planExpiresAt} = fin del período + {@link #GRACE}.
     *       El fin de período no se mueve: es la fecha que se debe. La nueva de un cambio de período fija el corte
     *       aunque la cuenta tenga otro plan: es la que tiene que pagar.</li>
     *   <li>CANCELLED con período pagado por delante: el plan <b>se conserva</b> y se fija
     *       {@code planExpiresAt} = fin del período; pasado ese instante rige TRIAL ({@link Account#effectivePlan}).
     *       Sin período pagado (nunca se activó, ya venció, o estaba en mora) baja a TRIAL en el acto.</li>
     * </ul>
     * CANCELLED es terminal, y un aviso con el mismo estado solo refresca el fin de período.
     * Solo se toca el plan si es el que esta suscripción pagaba: no pisa uno puesto a mano.
     */
    private Subscription applyStatus(Subscription subscription, SubscriptionStatus target, Instant nextPaymentDate) {
        if (subscription.getStatus() == SubscriptionStatus.CANCELLED) {
            return subscription;
        }
        if (subscription.getStatus() == target) {
            if (target == SubscriptionStatus.ACTIVE && nextPaymentDate != null
                    && !nextPaymentDate.equals(subscription.getCurrentPeriodEnd())) {
                subscription.setCurrentPeriodEnd(nextPaymentDate);
                return subscriptionRepository.save(subscription);
            }
            return subscription;
        }

        Instant now = clock.instant();
        SubscriptionStatus previous = subscription.getStatus();
        subscription.setStatus(target);
        if (target == SubscriptionStatus.ACTIVE) {
            subscription.setCurrentPeriodEnd(nextPaymentDate != null ? nextPaymentDate
                    : addMonths(now, subscription.getBillingPeriod().months()));
        }
        Subscription saved = subscriptionRepository.save(subscription);

        Account account = accountOf(saved);
        switch (target) {
            case ACTIVE -> {
                account.setPlan(saved.getPlanTier());
                account.setPlanExpiresAt(null);
            }
            case PAST_DUE -> {
                if (account.getPlan() == saved.getPlanTier() || previous == SubscriptionStatus.SCHEDULED) {
                    account.setPlanExpiresAt(saved.getCurrentPeriodEnd().plus(GRACE));
                }
            }
            case CANCELLED -> {
                if (account.getPlan() == saved.getPlanTier()) {
                    Instant paidUntil = saved.getCurrentPeriodEnd();
                    boolean paidPeriodLeft = previous == SubscriptionStatus.ACTIVE
                            && paidUntil != null && paidUntil.isAfter(now);
                    if (paidPeriodLeft) {
                        account.setPlanExpiresAt(paidUntil);
                    } else {
                        account.setPlan(PlanTier.TRIAL);
                        account.setPlanExpiresAt(null);
                    }
                }
            }
            case PENDING, SCHEDULED -> { }
        }
        accountRepository.save(account);

        AuditAction action = switch (target) {
            case ACTIVE -> AuditAction.SUBSCRIPTION_ACTIVATED;
            case PAST_DUE -> AuditAction.SUBSCRIPTION_PAST_DUE;
            case CANCELLED -> AuditAction.SUBSCRIPTION_CANCELLED;
            case PENDING, SCHEDULED -> null;
        };
        if (action != null) auditService.log(action, "Subscription", saved.getId(), auditPayload(saved));
        if (target == SubscriptionStatus.ACTIVE) notifyActivation(saved, previous);
        return saved;
    }

    /**
     * Bienvenida al activarse por primera vez; "recibimos tu pago" al salir de la mora. Una renovación no
     * pasa por acá (no cambia el estado) y no lleva mail: el comprobante lo manda MP. La nueva de un cambio de período
     * (SCHEDULED) tampoco: su mail es "tu plan cambió" ({@link #markApplied}).
     * Sale una vez por transición, y recién cuando la transacción se confirma (ver {@code TransactionalEmailService}).
     */
    private void notifyActivation(Subscription subscription, SubscriptionStatus previous) {
        User owner = ownerOf(subscription.getAccountId());
        String planName = planName(subscription.getPlanTier());
        if (previous == SubscriptionStatus.PENDING) {
            emailService.sendSubscriptionWelcome(owner.getEmail(), owner.getName(), planName,
                    subscription.getAmountArs(), subscription.getBillingPeriod(), appLink());
        } else if (previous == SubscriptionStatus.PAST_DUE) {
            emailService.sendPaymentRecovered(owner.getEmail(), owner.getName(), planName, appLink());
        }
    }

    private void notifyPlanChanged(Subscription subscription) {
        User owner = ownerOf(subscription.getAccountId());
        emailService.sendPlanChanged(owner.getEmail(), owner.getName(), planName(subscription.getPlanTier()),
                subscription.getAmountArs(), subscription.getBillingPeriod(), appLink());
    }

    private Account accountOf(Subscription subscription) {
        return accountRepository.findById(subscription.getAccountId())
                .orElseThrow(() -> new IllegalStateException("Account not found for subscription " + subscription.getId()));
    }

    private User ownerOf(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Account not found " + accountId));
        return userRepository.findById(account.getOwnerUserId())
                .orElseThrow(() -> new IllegalStateException("Account owner not found"));
    }

    /** STARTER → "Starter", para los mails. */
    private static String planName(PlanTier tier) {
        return tier.name().charAt(0) + tier.name().substring(1).toLowerCase();
    }

    private String planLink() {
        return frontendUrl + "/plan";
    }

    private String appLink() {
        return frontendUrl + "/dashboard";
    }

    private static Map<String, Object> auditPayload(Subscription s) {
        // HashMap: Map.of() tira NPE con valores null y currentPeriodEnd puede serlo.
        Map<String, Object> payload = new HashMap<>();
        payload.put("planTier", s.getPlanTier().name());
        payload.put("currentPeriodEnd", s.getCurrentPeriodEnd() == null ? null : s.getCurrentPeriodEnd().toString());
        return payload;
    }

    private static Map<String, Object> changePayload(SubscriptionChangeType type, PlanTier planTier,
                                                     BillingPeriod billingPeriod, BigDecimal amountArs) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("changeType", type.name());
        payload.put("planTier", planTier.name());
        payload.put("billingPeriod", billingPeriod.name());
        payload.put("amountArs", amountArs);
        return payload;
    }

    /** Suma meses de calendario (15/08 + 1 mes = 15/09), no de 30 días. */
    private static Instant addMonths(Instant from, int months) {
        return OffsetDateTime.ofInstant(from, ZoneOffset.UTC).plusMonths(months).toInstant();
    }

    private void cancelAtProvider(Subscription subscription) {
        if (subscription.getProviderSubscriptionId() == null) return;
        try {
            paymentProvider.cancelSubscription(subscription.getProviderSubscriptionId());
        } catch (PaymentProviderException e) {
            log.error("[PAYMENTS] could not cancel subscription {} at provider: {}",
                    subscription.getProviderSubscriptionId(), e.getMessage(), e);
            throw providerUnavailable();
        }
    }

    private void updateAmountAtProvider(Subscription subscription, BigDecimal amountArs) {
        try {
            paymentProvider.updateSubscriptionAmount(subscription.getProviderSubscriptionId(), amountArs);
        } catch (PaymentProviderException e) {
            log.error("[PAYMENTS] could not update amount of subscription {} at provider: {}",
                    subscription.getProviderSubscriptionId(), e.getMessage(), e);
            throw providerUnavailable();
        }
    }

    private void setEndDateAtProvider(Subscription subscription, Instant endDate) {
        try {
            paymentProvider.setSubscriptionEndDate(subscription.getProviderSubscriptionId(), endDate);
        } catch (PaymentProviderException e) {
            log.error("[PAYMENTS] could not set end date of subscription {} at provider: {}",
                    subscription.getProviderSubscriptionId(), e.getMessage(), e);
            throw providerUnavailable();
        }
    }

    private static AppException providerUnavailable() {
        return new AppException(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE,
                "The payment provider is not available right now, try again in a moment");
    }

    private void requireOwner(Account account) {
        if (!account.getOwnerUserId().equals(currentUserProvider.currentUserId())) {
            throw AppException.forbidden(ErrorCode.NOT_ACCOUNT_OWNER,
                    "Only the account owner can manage the subscription");
        }
    }

    private Account currentAccount() {
        UUID accountId = restaurantRepository.findByIdAndIsActiveTrue(TenantContext.require())
                .orElseThrow(AppException::sessionRestaurantGone)
                .getAccountId();
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Account not found"));
    }
}
