package com.chefcontrol.domain.payments;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository {

    /** Guarda y fuerza el flush: el índice de "una vigente por cuenta" exige que cancelar la vieja llegue antes que crear la nueva. */
    Subscription save(Subscription subscription);

    Optional<Subscription> findByProviderSubscriptionId(String provider, String providerSubscriptionId);

    /** La suscripción vigente (PENDING, ACTIVE o PAST_DUE) de la cuenta; como mucho una. */
    Optional<Subscription> findLiveByAccountId(UUID accountId);

    Optional<Subscription> findLatestByAccountId(UUID accountId);

    /** Las que la tarea de mora tiene que mirar: ACTIVE con el período pagado ya terminado, y todas las PAST_DUE. */
    List<Subscription> findOverdue(Instant now);
}
