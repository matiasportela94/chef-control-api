package com.chefcontrol.domain.payments;

public enum OneTimePaymentStatus {
    /** Checkout abierto, o un intento de pago que el proveedor todavía no resolvió. */
    PENDING,
    /** El proveedor lo aprobó y ya se aplicó lo que pagaba. Se aplica una sola vez. */
    APPROVED,
    /** Reemplazado por otro checkout del mismo propósito antes de pagarse. */
    CANCELLED
}
