package com.chefcontrol.domain.payments;

public enum SubscriptionStatus {
    /** Creada, esperando que el cliente cargue su tarjeta en el checkout del proveedor. */
    PENDING,
    /** Tarjeta cargada y cobrando: es la que da derecho al plan. */
    ACTIVE,
    /**
     * En mora: el período pagado terminó y el cobro no entró. La cuenta conserva el plan durante la
     * gracia (3 días); si para entonces no pagó, la tarea diaria la cancela y baja la cuenta a TRIAL.
     */
    PAST_DUE,
    /** Terminada, por el cliente, por el proveedor (rechazos seguidos), por mora o por cierre de cuenta. */
    CANCELLED;

    /** Ocupa el único lugar de "suscripción vigente" de la cuenta (ver índice de V28). */
    public boolean isLive() {
        return this != CANCELLED;
    }
}
