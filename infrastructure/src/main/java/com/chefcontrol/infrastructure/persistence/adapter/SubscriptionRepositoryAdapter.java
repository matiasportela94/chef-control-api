package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.payments.Subscription;
import com.chefcontrol.domain.payments.SubscriptionRepository;
import com.chefcontrol.domain.payments.SubscriptionStatus;
import com.chefcontrol.infrastructure.persistence.entity.SubscriptionJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Repository
@RequiredArgsConstructor
public class SubscriptionRepositoryAdapter implements SubscriptionRepository {

    private static final List<SubscriptionStatus> LIVE = Arrays.stream(SubscriptionStatus.values())
            .filter(SubscriptionStatus::isLive).toList();

    private final JpaSubscriptionRepository jpa;

    /** saveAndFlush: ver el contrato en {@link SubscriptionRepository#save}. */
    @Override
    public Subscription save(Subscription subscription) {
        return jpa.saveAndFlush(SubscriptionJpaEntity.from(subscription)).toDomain();
    }

    @Override
    public Optional<Subscription> findByProviderSubscriptionId(String provider, String providerSubscriptionId) {
        return jpa.findByProviderAndProviderSubscriptionId(provider, providerSubscriptionId)
                .map(SubscriptionJpaEntity::toDomain);
    }

    @Override
    public Optional<Subscription> findLiveByAccountId(UUID accountId) {
        return jpa.findFirstByAccountIdAndStatusIn(accountId, LIVE).map(SubscriptionJpaEntity::toDomain);
    }

    @Override
    public Optional<Subscription> findLatestByAccountId(UUID accountId) {
        return jpa.findFirstByAccountIdOrderByCreatedAtDesc(accountId).map(SubscriptionJpaEntity::toDomain);
    }

    @Override
    public List<Subscription> findOverdue(Instant now) {
        return Stream.concat(
                        jpa.findByStatusAndCurrentPeriodEndLessThanEqual(SubscriptionStatus.ACTIVE, now).stream(),
                        jpa.findByStatus(SubscriptionStatus.PAST_DUE).stream())
                .map(SubscriptionJpaEntity::toDomain).toList();
    }
}
