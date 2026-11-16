package com.chefcontrol.application.payments;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.payments.PaymentProvider.CreatedSubscription;
import com.chefcontrol.application.payments.PaymentProvider.NewSubscription;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
 */
@Slf4j
@Service
public class SubscriptionService {

    public record Checkout(Subscription subscription, String checkoutUrl) {}

    /**
     * Cuánto conserva el plan una cuenta en mora después de que terminó lo pagado (decidido por Mati,
     * 2026-10-12): "se intenta el día que vence, tres días seguidos, y al tercero baja a TRIAL".
     * Vence el 15/09 a las 17:00 → plan hasta el 18/09 a las 17:00.
     */
    static final Duration GRACE = Duration.ofDays(3);

    private final SubscriptionRepository subscriptionRepository;
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
            cancelAtProvider(live);
            live.setStatus(SubscriptionStatus.CANCELLED);
            subscriptionRepository.save(live);
        });

        Subscription saved = subscriptionRepository.save(Subscription.builder()
                .accountId(account.getId())
                .planTier(planTier)
                .billingPeriod(billingPeriod)
                .status(SubscriptionStatus.PENDING)
                .provider(paymentProvider.name())
                .amountArs(amount)
                .payerEmail(owner.getEmail())
                .build());

        CreatedSubscription created;
        try {
            created = paymentProvider.createSubscription(new NewSubscription(
                    saved.getId().toString(),
                    "Chef Control - Plan " + planTier.name() + (billingPeriod == BillingPeriod.ANNUAL ? " (anual)" : ""),
                    amount, billingPeriod.months(), owner.getEmail(), planLink()));
        } catch (PaymentProviderException e) {
            // Se relanza: la transacción se revierte y la fila PENDING no queda huérfana.
            log.error("[PAYMENTS] could not create subscription at provider: {}", e.getMessage(), e);
            throw providerUnavailable();
        }

        saved.setProviderSubscriptionId(created.providerSubscriptionId());
        saved = subscriptionRepository.save(saved);

        auditService.log(AuditAction.SUBSCRIPTION_CREATED, "Subscription", saved.getId(),
                Map.of("planTier", planTier.name(), "billingPeriod", billingPeriod.name(), "amountArs", amount));
        return new Checkout(saved, created.checkoutUrl());
    }

    @Transactional(readOnly = true)
    public Optional<Subscription> getSubscription() {
        return subscriptionRepository.findLatestByAccountId(currentAccount().getId());
    }

    /**
     * Cancela la suscripción vigente: deja de cobrarse, pero la cuenta conserva el plan que pagó hasta
     * que venza el período ya abonado ({@link #applyStatus}).
     */
    @Transactional
    public Subscription cancelSubscription() {
        Account account = currentAccount();
        requireOwner(account);

        Subscription live = subscriptionRepository.findLiveByAccountId(account.getId())
                .orElseThrow(() -> AppException.notFound(ErrorCode.SUBSCRIPTION_NOT_FOUND,
                        "The account has no active subscription"));

        cancelAtProvider(live);
        return applyStatus(live, SubscriptionStatus.CANCELLED, null);
    }

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
     * Cancela en el proveedor la suscripción vigente antes de que se cierre la cuenta. Si no se
     * pudiera, aborta: borrar la cuenta sin cancelarla dejaría un cobro mensual a nombre de
     * alguien que ya no existe. Las filas locales se van solas con la cascada de la cuenta.
     */
    @Transactional
    public void cancelBeforeAccountClosure(UUID accountId) {
        subscriptionRepository.findLiveByAccountId(accountId).ifPresent(this::cancelAtProvider);
    }

    /** Lo que recorre la tarea diaria de mora ({@link OverdueSubscriptionsJob}). */
    @Transactional(readOnly = true)
    public List<Subscription> findOverdueSubscriptions() {
        return subscriptionRepository.findOverdue(clock.instant());
    }

    /**
     * Un paso de la tarea diaria de mora sobre una suscripción cuyo período pagado terminó.
     * Primero relee el proveedor: si el cobro entró vuelve a ACTIVE y no pasa nada más. Si sigue sin
     * pagar, dentro de la gracia manda el recordatorio del día (corre una vez por día, así que son tres);
     * pasada la gracia la cancela en el proveedor —para que no cobre después de haber bajado al
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
        applyStatus(subscription, SubscriptionStatus.CANCELLED, null);
        emailService.sendPlanDowngradedToTrial(owner.getEmail(), owner.getName(), planName, planLink());
    }

    /** Relee la suscripción en el proveedor y copia el estado, detectando la mora. */
    private Subscription reconcile(Subscription local) {
        ProviderSubscription remote = paymentProvider.getSubscription(local.getProviderSubscriptionId());
        if (remote.status() == null) {
            log.warn("[PAYMENTS] subscription {} is in a provider state we do not handle, no change",
                    local.getProviderSubscriptionId());
            return local;
        }
        SubscriptionStatus target = remote.status() == SubscriptionStatus.ACTIVE && isUnpaid(local, remote.nextPaymentDate())
                ? SubscriptionStatus.PAST_DUE : remote.status();
        return applyStatus(local, target, remote.nextPaymentDate());
    }

    /**
     * Autorizada en el proveedor pero sin cobrar: el próximo cobro que informa ya pasó, o sea que el
     * de este período no entró. Solo para una suscripción que ya estuvo activa. Sin fecha no se presume
     * mora: un dato faltante no le corta el acceso a quien paga (y si de verdad no paga, MP la cancela
     * solo tras 3 cobros rechazados).
     */
    private boolean isUnpaid(Subscription local, Instant nextPaymentDate) {
        return (local.getStatus() == SubscriptionStatus.ACTIVE || local.getStatus() == SubscriptionStatus.PAST_DUE)
                && nextPaymentDate != null && !nextPaymentDate.isAfter(clock.instant());
    }

    /**
     * Lleva la suscripción local al estado {@code target} y ajusta el plan de la cuenta.
     *
     * <ul>
     *   <li>ACTIVE: la cuenta pasa al plan contratado, sin corte (también al salir de la mora). El fin de
     *       período es el próximo cobro que informa el proveedor; si no lo informó, una estimación (hoy + período).</li>
     *   <li>PAST_DUE: el plan se conserva durante la gracia: {@code planExpiresAt} = fin del período + {@link #GRACE}.
     *       El fin de período no se mueve: es la fecha que se debe.</li>
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

        Account account = accountRepository.findById(saved.getAccountId())
                .orElseThrow(() -> new IllegalStateException("Account not found for subscription " + saved.getId()));
        switch (target) {
            case ACTIVE -> {
                account.setPlan(saved.getPlanTier());
                account.setPlanExpiresAt(null);
            }
            case PAST_DUE -> {
                if (account.getPlan() == saved.getPlanTier()) {
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
            case PENDING -> { }
        }
        accountRepository.save(account);

        AuditAction action = switch (target) {
            case ACTIVE -> AuditAction.SUBSCRIPTION_ACTIVATED;
            case PAST_DUE -> AuditAction.SUBSCRIPTION_PAST_DUE;
            case CANCELLED -> AuditAction.SUBSCRIPTION_CANCELLED;
            case PENDING -> null;
        };
        if (action != null) auditService.log(action, "Subscription", saved.getId(), auditPayload(saved));
        if (target == SubscriptionStatus.ACTIVE) notifyActivation(saved, previous);
        return saved;
    }

    /**
     * Bienvenida al activarse por primera vez; "recibimos tu pago" al salir de la mora. Una renovación no
     * pasa por acá (no cambia el estado) y no lleva mail: el comprobante lo manda MP.
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
                .orElseThrow(() -> new IllegalStateException("Restaurant not found"))
                .getAccountId();
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Account not found"));
    }
}
