package com.chefcontrol.domain.payments;

/** Qué cambia al pasar de la suscripción vigente al plan y período pedidos. */
public enum SubscriptionChangeType {
    /** Plan más caro, mismo período: inmediata, pagando hoy la diferencia prorrateada (pago único). */
    UPGRADE,
    /** Plan más barato, mismo período: al fin del período; se cambia el monto de la suscripción vigente. */
    DOWNGRADE,
    /** Mismo plan, otro período: al fin del período, con una suscripción nueva (MP no cambia la frecuencia). */
    PERIOD_CHANGE,
    /** Plan más barato y otro período: como PERIOD_CHANGE, con el plan nuevo. */
    DOWNGRADE_AND_PERIOD_CHANGE;

    public boolean isPeriodChange() {
        return this == PERIOD_CHANGE || this == DOWNGRADE_AND_PERIOD_CHANGE;
    }
}
