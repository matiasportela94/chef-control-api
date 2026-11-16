package com.chefcontrol.domain.payments;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository {

    /** Guarda y fuerza el flush: el índice de "una vigente por cuenta" exige que cancelar la vieja llegue antes que crear la nueva. */
    Subscription save(Subscription subscription);

    Optional<Subscription> findByProviderSubscriptionId(String provider, String providerSubscriptionId);

    Optional<Subscription> findById(UUID id);

    /** La suscripción vigente de la cuenta (ver {@link Subscription#isLive()}); como mucho una. */
    Optional<Subscription> findLiveByAccountId(UUID accountId);

    /**
     * La última que se cobra o se cobró, para mostrar: deja afuera la de un cambio de período que todavía no arrancó
     * (o que se descartó sin arrancar), que se muestra como el cambio pendiente.
     */
    Optional<Subscription> findLatestCurrentByAccountId(UUID accountId);

    /**
     * Las que la tarea de mora tiene que mirar: ACTIVE con el período pagado ya terminado, todas las PAST_DUE, y las
     * de un cambio de período (PENDING o SCHEDULED) cuyo arranque ya pasó.
     */
    List<Subscription> findOverdue(Instant now);
}
