package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.domain.payments.ChangeRequestStatus;
import com.chefcontrol.infrastructure.persistence.entity.SubscriptionChangeRequestJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaSubscriptionChangeRequestRepository extends JpaRepository<SubscriptionChangeRequestJpaEntity, UUID> {

    Optional<SubscriptionChangeRequestJpaEntity> findFirstByAccountIdAndStatus(UUID accountId, ChangeRequestStatus status);
}
