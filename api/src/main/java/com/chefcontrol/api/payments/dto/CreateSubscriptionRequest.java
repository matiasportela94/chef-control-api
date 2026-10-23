package com.chefcontrol.api.payments.dto;

import com.chefcontrol.domain.payments.BillingPeriod;
import com.chefcontrol.domain.plan.PlanTier;
import jakarta.validation.constraints.NotNull;

/** @param billingPeriod opcional: sin él es MONTHLY */
public record CreateSubscriptionRequest(
        @NotNull PlanTier planTier,
        BillingPeriod billingPeriod
) {}
