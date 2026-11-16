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
    private Instant createdAt;
    private Instant updatedAt;

    public boolean isLive() {
        return status.isLive();
    }
}
