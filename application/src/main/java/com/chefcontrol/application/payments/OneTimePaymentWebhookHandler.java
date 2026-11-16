package com.chefcontrol.application.payments;

import com.chefcontrol.application.payments.PaymentProvider.ProviderPayment;
import com.chefcontrol.domain.payments.OneTimePayment;
import com.chefcontrol.domain.payments.OneTimePaymentRepository;
import com.chefcontrol.domain.payments.OneTimePaymentStatus;
import com.chefcontrol.domain.payments.PaymentWebhookEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Avisos de pagos únicos de MercadoPago (tópico {@code payment}; {@code data.id} es el id del pago). Como con las
 * suscripciones, no se confía en el aviso: se relee el pago y se aplica solo si está aprobado, por el monto exacto, y
 * una sola vez. La parte genérica termina en marcarlo APPROVED; qué se aplica lo decide su {@code purpose}.
 *
 * <p>Un pago de un checkout reemplazado (CANCELLED) se aplica igual: el cliente pagó ese y quizás no el nuevo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OneTimePaymentWebhookHandler implements WebhookEventHandler {

    static final String PAYMENT_TOPIC = "payment";

    private final PaymentProvider paymentProvider;
    private final OneTimePaymentRepository oneTimePaymentRepository;
    private final SubscriptionService subscriptionService;

    @Override
    public boolean handles(PaymentWebhookEvent event) {
        return PaymentProvider.MERCADOPAGO.equals(event.provider()) && PAYMENT_TOPIC.equals(event.topic());
    }

    @Override
    public void handle(PaymentWebhookEvent event) {
        if (event.dataId() == null || event.dataId().isBlank()) {
            log.warn("[PAYMENTS] payment notification without data.id, nothing to do");
            return;
        }
        ProviderPayment remote = paymentProvider.getPayment(event.dataId());
        if (!remote.approved()) {
            log.info("[PAYMENTS] payment {} is not approved yet, nothing to apply", remote.providerPaymentId());
            return;
        }
        OneTimePayment payment = ownPayment(remote.externalReference()).orElse(null);
        if (payment == null) {
            // Un pago que no salió de un checkout nuestro (u otro entorno con la misma aplicación de MP).
            log.warn("[PAYMENTS] approved payment {} with unknown external_reference {}, ignoring",
                    remote.providerPaymentId(), remote.externalReference());
            return;
        }
        if (payment.getStatus() == OneTimePaymentStatus.APPROVED) return;
        if (remote.amountArs() == null || remote.amountArs().compareTo(payment.getAmountArs()) != 0
                || !"ARS".equals(remote.currency())) {
            log.error("[PAYMENTS] payment {} approved for {} {} but one-time payment {} expects {} ARS: not applied",
                    remote.providerPaymentId(), remote.amountArs(), remote.currency(), payment.getId(), payment.getAmountArs());
            return;
        }

        payment.setStatus(OneTimePaymentStatus.APPROVED);
        payment.setProviderPaymentId(remote.providerPaymentId());
        OneTimePayment saved = oneTimePaymentRepository.save(payment);

        // ponytail: un solo propósito hoy; con el segundo (Bristol), un mapa purpose → quien lo aplica.
        if (OneTimePayment.PLAN_UPGRADE.equals(saved.getPurpose())) {
            subscriptionService.applyPaidUpgrade(saved);
        } else {
            log.error("[PAYMENTS] one-time payment {} approved with unknown purpose {}", saved.getId(), saved.getPurpose());
        }
    }

    private Optional<OneTimePayment> ownPayment(String externalReference) {
        try {
            return externalReference == null ? Optional.empty()
                    : oneTimePaymentRepository.findById(UUID.fromString(externalReference));
        } catch (IllegalArgumentException notAnId) {
            return Optional.empty();
        }
    }
}
