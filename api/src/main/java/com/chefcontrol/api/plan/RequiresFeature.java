package com.chefcontrol.api.plan;

import com.chefcontrol.domain.plan.Feature;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * El endpoint (o todo el controller) es de una feature paga: si el plan efectivo de la cuenta no la incluye,
 * responde 403 PLAN_FEATURE_REQUIRED. Lo aplica {@link PlanFeatureInterceptor}.
 *
 * Se gatea en la entrada HTTP y no en los servicios a propósito: los servicios se usan también por dentro
 * (una venta descuenta stock con la receta aunque la cuenta haya bajado de plan) y cortarlos ahí rompería
 * datos, no solo pantallas.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresFeature {
    Feature value();
}
