package com.chefcontrol.domain.payments;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionChangeRequestRepository {

    SubscriptionChangeRequest save(SubscriptionChangeRequest request);

    /** Como mucho una por cuenta (índice parcial de V31). */
    Optional<SubscriptionChangeRequest> findPendingByAccountId(UUID accountId);
}
