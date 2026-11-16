package com.chefcontrol.domain.payments;

public enum ChangeRequestStatus {
    /** Pedida: se aplica cuando entra el cobro del fin del período. Hasta ahí se reemplaza o se cancela sin costo. */
    PENDING,
    /** El cobro del período nuevo entró y la cuenta ya tiene el plan pedido. */
    APPLIED,
    /** Cancelada por el dueño, reemplazada por otra o caída con la suscripción que la iba a cobrar. */
    CANCELLED
}
