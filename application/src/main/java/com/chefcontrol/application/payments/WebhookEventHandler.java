package com.chefcontrol.application.payments;

import com.chefcontrol.domain.payments.PaymentWebhookEvent;

/**
 * Actúa sobre un tipo de aviso de un proveedor de pago ya autenticado. Si lanza, el aviso se
 * descarta junto con la transacción, el webhook responde error y el proveedor lo reintenta:
 * un evento solo queda registrado como recibido si se pudo procesar.
 */
public interface WebhookEventHandler {

    boolean handles(PaymentWebhookEvent event);

    void handle(PaymentWebhookEvent event);
}
