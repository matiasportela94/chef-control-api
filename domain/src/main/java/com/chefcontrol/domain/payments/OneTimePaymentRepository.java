package com.chefcontrol.domain.payments;

import java.util.Optional;
import java.util.UUID;

public interface OneTimePaymentRepository {

    OneTimePayment save(OneTimePayment payment);

    Optional<OneTimePayment> findById(UUID id);

    /** El último checkout sin pagar de ese propósito. */
    Optional<OneTimePayment> findLatestPendingByAccountIdAndPurpose(UUID accountId, String purpose);
}
