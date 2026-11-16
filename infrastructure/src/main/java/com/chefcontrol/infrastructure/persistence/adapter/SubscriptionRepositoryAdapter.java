package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.payments.Subscription;
import com.chefcontrol.domain.payments.SubscriptionRepository;
import com.chefcontrol.domain.payments.SubscriptionStatus;
import com.chefcontrol.infrastructure.persistence.entity.SubscriptionJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Repository
@RequiredArgsConstructor
public class SubscriptionRepositoryAdapter implements SubscriptionRepository {

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
    public Optional<Subscription> findById(UUID id) {
        return jpa.findById(id).map(SubscriptionJpaEntity::toDomain);
    }

    @Override
    public Optional<Subscription> findLiveByAccountId(UUID accountId) {
        return jpa.findLiveByAccountId(accountId).map(SubscriptionJpaEntity::toDomain);
    }

    @Override
    public Optional<Subscription> findLatestCurrentByAccountId(UUID accountId) {
        return jpa.findLatestCurrentByAccountId(accountId, Limit.of(1)).stream().findFirst()
                .map(SubscriptionJpaEntity::toDomain);
    }

    @Override
    public List<Subscription> findOverdue(Instant now) {
        return Stream.of(
                        jpa.findByStatusAndCurrentPeriodEndLessThanEqual(SubscriptionStatus.ACTIVE, now),
                        jpa.findByStatus(SubscriptionStatus.PAST_DUE),
                        jpa.findByStatusInAndStartsAtLessThanEqual(
                                List.of(SubscriptionStatus.PENDING, SubscriptionStatus.SCHEDULED), now))
                .flatMap(List::stream)
                .map(SubscriptionJpaEntity::toDomain).toList();
    }
}
