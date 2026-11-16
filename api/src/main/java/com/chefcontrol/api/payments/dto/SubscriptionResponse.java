package com.chefcontrol.api.payments.dto;

import com.chefcontrol.application.payments.SubscriptionService.PendingChange;
import com.chefcontrol.domain.payments.BillingPeriod;
import com.chefcontrol.domain.payments.Subscription;
import com.chefcontrol.domain.payments.SubscriptionStatus;
import com.chefcontrol.domain.plan.PlanTier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * @param currentPeriodEnd cuándo termina lo ya pagado (el próximo cobro); null si todavía no se activó
 * @param pendingChange    el cambio de plan o de período programado para el fin del período; null si no hay
 */
public record SubscriptionResponse(
        UUID id,
        PlanTier planTier,
        BillingPeriod billingPeriod,
        SubscriptionStatus status,
        BigDecimal amountArs,
        Instant currentPeriodEnd,
        Instant createdAt,
        PendingSubscriptionChangeResponse pendingChange
) {
    public static SubscriptionResponse from(Subscription s) {
        return from(s, null);
    }

    public static SubscriptionResponse from(Subscription s, PendingChange pendingChange) {
        return new SubscriptionResponse(s.getId(), s.getPlanTier(), s.getBillingPeriod(), s.getStatus(),
                s.getAmountArs(), s.getCurrentPeriodEnd(), s.getCreatedAt(),
                PendingSubscriptionChangeResponse.from(pendingChange));
    }
}
