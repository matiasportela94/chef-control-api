package com.chefcontrol.api.payments.dto;

import com.chefcontrol.application.payments.SubscriptionService.ChangeResult;
import com.chefcontrol.domain.payments.SubscriptionChangeType;

/**
 * @param checkoutUrl   UPGRADE: el checkout del pago único · *PERIOD_CHANGE: el de la suscripción nueva · DOWNGRADE: null
 * @param pendingChange el cambio programado; null en UPGRADE
 */
public record SubscriptionChangeResponse(
        SubscriptionChangeType changeType,
        String checkoutUrl,
        PendingSubscriptionChangeResponse pendingChange
) {
    public static SubscriptionChangeResponse from(ChangeResult r) {
        return new SubscriptionChangeResponse(r.changeType(), r.checkoutUrl(),
                PendingSubscriptionChangeResponse.from(r.pendingChange()));
    }
}
