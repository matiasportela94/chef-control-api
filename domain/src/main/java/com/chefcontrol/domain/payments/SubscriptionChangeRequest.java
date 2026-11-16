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
 * Un cambio de plan que rige al terminar el período pagado ({@code effectiveAt}) y se aplica recién cuando entra ese
 * cobro: una baja de plan (se cambia el monto de la suscripción vigente) o un cambio de período, mensual ↔ anual (una
 * suscripción nueva, {@code newSubscriptionId}, que arranca en {@code effectiveAt}). Las subas no pasan por acá: son
 * inmediatas, con un pago único.
 *
 * @param subscriptionId la suscripción vigente cuando se pidió: solo sus avisos aplican o tiran una baja
 */
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SubscriptionChangeRequest {
    private UUID id;
    private UUID accountId;
    private UUID subscriptionId;
    private SubscriptionChangeType changeType;
    private PlanTier planTier;
    private BillingPeriod billingPeriod;
    private BigDecimal amountArs;
    private Instant effectiveAt;
    private ChangeRequestStatus status;
    private UUID newSubscriptionId;
    private Instant createdAt;
    private Instant updatedAt;

    public boolean isPeriodChange() {
        return changeType.isPeriodChange();
    }
}
