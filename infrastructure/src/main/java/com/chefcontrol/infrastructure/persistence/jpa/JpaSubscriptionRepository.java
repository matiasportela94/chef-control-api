package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.domain.payments.SubscriptionStatus;
import com.chefcontrol.infrastructure.persistence.entity.SubscriptionJpaEntity;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaSubscriptionRepository extends JpaRepository<SubscriptionJpaEntity, UUID> {

    Optional<SubscriptionJpaEntity> findByProviderAndProviderSubscriptionId(String provider, String providerSubscriptionId);

    /** La vigente: la misma condición que el índice de V31 y que {@code Subscription.isLive()}. */
    @Query("""
            select s from SubscriptionJpaEntity s
            where s.accountId = :accountId
              and (s.status in (com.chefcontrol.domain.payments.SubscriptionStatus.ACTIVE,
                                com.chefcontrol.domain.payments.SubscriptionStatus.PAST_DUE)
                   or (s.status = com.chefcontrol.domain.payments.SubscriptionStatus.PENDING and s.startsAt is null))
            """)
    Optional<SubscriptionJpaEntity> findLiveByAccountId(@Param("accountId") UUID accountId);

    /** Sin la de un cambio de período que no arrancó: esa tiene starts_at y nunca tuvo período pagado. */
    @Query("""
            select s from SubscriptionJpaEntity s
            where s.accountId = :accountId and (s.startsAt is null or s.currentPeriodEnd is not null)
            order by s.createdAt desc
            """)
    List<SubscriptionJpaEntity> findLatestCurrentByAccountId(@Param("accountId") UUID accountId, Limit limit);

    List<SubscriptionJpaEntity> findByStatusAndCurrentPeriodEndLessThanEqual(SubscriptionStatus status, Instant periodEnd);

    List<SubscriptionJpaEntity> findByStatus(SubscriptionStatus status);

    List<SubscriptionJpaEntity> findByStatusInAndStartsAtLessThanEqual(Collection<SubscriptionStatus> statuses, Instant startsAt);
}
