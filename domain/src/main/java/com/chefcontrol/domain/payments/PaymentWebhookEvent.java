package com.chefcontrol.domain.payments;

/** Un aviso de un proveedor de pago, ya autenticado, tal como llegó. */
public record PaymentWebhookEvent(String provider, String eventKey, String topic, String dataId, String payload) {
}
