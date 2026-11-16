package com.chefcontrol.api.payments.dto;

import com.chefcontrol.domain.payments.BillingPeriod;
import com.chefcontrol.domain.plan.PlanTier;
import jakarta.validation.constraints.NotNull;

/** El plan y el período al que se quiere pasar. Los dos obligatorios: un cambio sin período sería ambiguo. */
public record ChangeSubscriptionRequest(
        @NotNull PlanTier planTier,
        @NotNull BillingPeriod billingPeriod
) {}
