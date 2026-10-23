package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.domain.payments.SubscriptionStatus;
import com.chefcontrol.infrastructure.persistence.entity.SubscriptionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaSubscriptionRepository extends JpaRepository<SubscriptionJpaEntity, UUID> {

    Optional<SubscriptionJpaEntity> findByProviderAndProviderSubscriptionId(String provider, String providerSubscriptionId);

    Optional<SubscriptionJpaEntity> findFirstByAccountIdAndStatusIn(UUID accountId, Collection<SubscriptionStatus> statuses);

    Optional<SubscriptionJpaEntity> findFirstByAccountIdOrderByCreatedAtDesc(UUID accountId);

    List<SubscriptionJpaEntity> findByStatusAndCurrentPeriodEndLessThanEqual(SubscriptionStatus status, Instant periodEnd);

    List<SubscriptionJpaEntity> findByStatus(SubscriptionStatus status);
}
