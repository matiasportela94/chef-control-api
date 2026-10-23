package com.chefcontrol.application.payments;

import com.chefcontrol.domain.payments.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarea diaria de mora (09:00 hora argentina por defecto, {@code app.payments.overdue-cron}): pasa por
 * cada suscripción cuyo período pagado terminó. Cada una en su propia transacción
 * ({@link SubscriptionService#collectOverdueSubscription}), así que si MP falla con una, las demás siguen
 * y esa se reintenta mañana. Es una clase aparte porque el {@code @Transactional} de cada paso solo
 * aplica si se llama desde afuera del servicio.
 *
 * <p>ponytail: asume una sola instancia del backend, como {@code AlertEvaluationService}. Con varias,
 * cada una mandaría su recordatorio: sumar un lock (ShedLock) el día que se escale horizontalmente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverdueSubscriptionsJob {

    private final SubscriptionService subscriptionService;

    @Scheduled(cron = "${app.payments.overdue-cron:0 0 9 * * *}", zone = "America/Argentina/Buenos_Aires")
    public void collectOverdueSubscriptions() {
        for (Subscription subscription : subscriptionService.findOverdueSubscriptions()) {
            try {
                subscriptionService.collectOverdueSubscription(subscription);
            } catch (RuntimeException e) {
                log.error("[PAYMENTS] overdue subscription {} not processed, retrying tomorrow: {}",
                        subscription.getId(), e.getMessage(), e);
            }
        }
    }
}
