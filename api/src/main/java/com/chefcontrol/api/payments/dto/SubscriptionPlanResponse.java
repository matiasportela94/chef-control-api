package com.chefcontrol.api.payments.dto;

import com.chefcontrol.domain.plan.PlanTier;

import java.math.BigDecimal;
import java.util.List;

/**
 * Un plan contratable con lo que se cobra hoy. Sale de la misma config que usa el cobro, así que la
 * pantalla nunca muestra un precio distinto del que se manda a MercadoPago.
 *
 * @param annualAmountArs lo que se cobra una vez por año (12 meses con el descuento anual)
 */
public record SubscriptionPlanResponse(
        PlanTier planTier,
        BigDecimal monthlyAmountArs,
        BigDecimal annualAmountArs,
        List<String> features
) {}
