package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.payments.ChangeRequestStatus;
import com.chefcontrol.domain.payments.SubscriptionChangeRequest;
import com.chefcontrol.domain.payments.SubscriptionChangeRequestRepository;
import com.chefcontrol.infrastructure.persistence.entity.SubscriptionChangeRequestJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaSubscriptionChangeRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SubscriptionChangeRequestRepositoryAdapter implements SubscriptionChangeRequestRepository {

    private final JpaSubscriptionChangeRequestRepository jpa;

    /** saveAndFlush: el índice de "una PENDING por cuenta" exige que cerrar la vieja llegue antes que crear la nueva. */
    @Override
    public SubscriptionChangeRequest save(SubscriptionChangeRequest request) {
        return jpa.saveAndFlush(SubscriptionChangeRequestJpaEntity.from(request)).toDomain();
    }

    @Override
    public Optional<SubscriptionChangeRequest> findPendingByAccountId(UUID accountId) {
        return jpa.findFirstByAccountIdAndStatus(accountId, ChangeRequestStatus.PENDING)
                .map(SubscriptionChangeRequestJpaEntity::toDomain);
    }
}
