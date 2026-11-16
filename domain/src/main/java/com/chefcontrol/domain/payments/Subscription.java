package com.chefcontrol.domain.payments;

import com.chefcontrol.domain.plan.PlanTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Suscripción de pago de una cuenta. Copia local del estado en el proveedor: la verdad la tiene
 * él y se sincroniza por webhook. {@code providerSubscriptionId} es null solo mientras se está
 * creando en el proveedor. {@code currentPeriodEnd} es el instante en que termina lo ya pagado
 * (el próximo cobro); null mientras no se activó.
 */
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Subscription {

    private UUID id;
    private UUID accountId;
    private PlanTier planTier;
    private BillingPeriod billingPeriod;
    private SubscriptionStatus status;
    private String provider;
    private String providerSubscriptionId;
    private BigDecimal amountArs;
    private String payerEmail;
    private Instant currentPeriodEnd;
    /** Solo en la que reemplaza a la vigente en un cambio de período: cuándo se cobra por primera vez (V31). */
    private Instant startsAt;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Ocupa el único lugar de "suscripción vigente" de la cuenta (ver índice de V31): la que se está cobrando o la que
     * se está contratando de cero. La de un cambio de período no, hasta que arranca.
     */
    public boolean isLive() {
        return status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.PAST_DUE
                || (status == SubscriptionStatus.PENDING && startsAt == null);
    }
}
