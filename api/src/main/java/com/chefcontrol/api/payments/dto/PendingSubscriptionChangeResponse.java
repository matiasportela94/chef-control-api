package com.chefcontrol.api.payments.dto;

import com.chefcontrol.application.payments.SubscriptionService.PendingChange;
import com.chefcontrol.domain.payments.BillingPeriod;
import com.chefcontrol.domain.payments.SubscriptionChangeType;
import com.chefcontrol.domain.plan.PlanTier;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un cambio programado para el fin del período (nunca una suba: esa es inmediata).
 *
 * @param amountArs        lo que se cobra por ciclo desde {@code effectiveAt}
 * @param awaitingCheckout cambio de período cuya suscripción nueva el cliente todavía no autorizó en MercadoPago
 */
public record PendingSubscriptionChangeResponse(
        SubscriptionChangeType changeType,
        PlanTier planTier,
        BillingPeriod billingPeriod,
        BigDecimal amountArs,
        Instant effectiveAt,
        boolean awaitingCheckout
) {
    public static PendingSubscriptionChangeResponse from(PendingChange p) {
        if (p == null) return null;
        var r = p.request();
        return new PendingSubscriptionChangeResponse(r.getChangeType(), r.getPlanTier(), r.getBillingPeriod(),
                r.getAmountArs(), r.getEffectiveAt(), p.awaitingCheckout());
    }
}
