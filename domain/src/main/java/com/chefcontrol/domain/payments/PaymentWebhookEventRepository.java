package com.chefcontrol.domain.payments;

public interface PaymentWebhookEventRepository {

    /** @return {@code true} si el aviso era nuevo; {@code false} si ya estaba (reintento del proveedor). */
    boolean saveIfNew(PaymentWebhookEvent event);
}
