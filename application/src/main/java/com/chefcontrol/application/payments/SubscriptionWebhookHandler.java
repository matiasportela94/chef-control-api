package com.chefcontrol.application.payments;

import com.chefcontrol.domain.payments.PaymentWebhookEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Avisos de suscripciones de MercadoPago. Los dos tipos terminan en lo mismo: volver a leer la
 * suscripción del proveedor ({@link SubscriptionService#syncSubscription}).
 * <ul>
 *   <li>{@code subscription_preapproval}: cambió la suscripción; su {@code data.id} es el id de la suscripción.</li>
 *   <li>{@code subscription_authorized_payment}: hubo un cobro (aprobado o rechazado); su {@code data.id} es
 *       el del cobro. No se mira el resultado del cobro: releer la suscripción ya dice si quedó pagada o en
 *       mora, y así quien paga durante la gracia recupera el acceso en el momento, sin esperar a la tarea diaria.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionWebhookHandler implements WebhookEventHandler {

    static final String SUBSCRIPTION_TOPIC = "subscription_preapproval";
    static final String CHARGE_TOPIC = "subscription_authorized_payment";

    private final SubscriptionService subscriptionService;
    private final PaymentProvider paymentProvider;

    @Override
    public boolean handles(PaymentWebhookEvent event) {
        return PaymentProvider.MERCADOPAGO.equals(event.provider())
                && (SUBSCRIPTION_TOPIC.equals(event.topic()) || CHARGE_TOPIC.equals(event.topic()));
    }

    @Override
    public void handle(PaymentWebhookEvent event) {
        if (event.dataId() == null || event.dataId().isBlank()) {
            log.warn("[PAYMENTS] {} notification without data.id, nothing to sync", event.topic());
            return;
        }
        subscriptionService.syncSubscription(CHARGE_TOPIC.equals(event.topic())
                ? paymentProvider.getChargeSubscriptionId(event.dataId())
                : event.dataId());
    }
}
