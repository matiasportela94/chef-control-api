package com.chefcontrol.api.payments.dto;

import com.chefcontrol.application.payments.SubscriptionService.ChangePreview;
import com.chefcontrol.domain.payments.BillingPeriod;
import com.chefcontrol.domain.payments.SubscriptionChangeType;
import com.chefcontrol.domain.plan.PlanTier;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Qué pasaría con un cambio de plan. El prorrateo lo calcula solo el backend.
 *
 * @param requiresCheckout el cambio redirige a MercadoPago (todo salvo DOWNGRADE)
 * @param chargeNowArs     solo UPGRADE: lo que se paga hoy
 * @param daysRemaining    días calendario que quedan del período (mínimo 1)
 * @param periodDays       días del período completo
 * @param effectiveAt      UPGRADE: ahora (al aprobarse el pago); el resto: el fin del período
 * @param nextChargeAt     el primer cobro con {@code newAmountArs}
 * @param newAmountArs     lo que se cobra por ciclo desde {@code nextChargeAt}
 */
public record SubscriptionChangePreviewResponse(
        SubscriptionChangeType changeType,
        PlanTier planTier,
        BillingPeriod billingPeriod,
        boolean requiresCheckout,
        BigDecimal chargeNowArs,
        int daysRemaining,
        int periodDays,
        Instant effectiveAt,
        Instant nextChargeAt,
        BigDecimal newAmountArs,
        boolean replacesPendingChange
) {
    public static SubscriptionChangePreviewResponse from(ChangePreview p) {
        return new SubscriptionChangePreviewResponse(p.changeType(), p.planTier(), p.billingPeriod(), p.requiresCheckout(),
                p.chargeNowArs(), p.daysRemaining(), p.periodDays(), p.effectiveAt(), p.nextChargeAt(), p.newAmountArs(),
                p.replacesPendingChange());
    }
}
