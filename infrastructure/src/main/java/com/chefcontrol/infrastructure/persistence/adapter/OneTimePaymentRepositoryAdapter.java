package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.payments.OneTimePayment;
import com.chefcontrol.domain.payments.OneTimePaymentRepository;
import com.chefcontrol.domain.payments.OneTimePaymentStatus;
import com.chefcontrol.infrastructure.persistence.entity.OneTimePaymentJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaOneTimePaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OneTimePaymentRepositoryAdapter implements OneTimePaymentRepository {

    private final JpaOneTimePaymentRepository jpa;

    @Override
    public OneTimePayment save(OneTimePayment payment) {
        return jpa.saveAndFlush(OneTimePaymentJpaEntity.from(payment)).toDomain();
    }

    @Override
    public Optional<OneTimePayment> findById(UUID id) {
        return jpa.findById(id).map(OneTimePaymentJpaEntity::toDomain);
    }

    @Override
    public Optional<OneTimePayment> findLatestPendingByAccountIdAndPurpose(UUID accountId, String purpose) {
        return jpa.findFirstByAccountIdAndPurposeAndStatusOrderByCreatedAtDesc(accountId, purpose, OneTimePaymentStatus.PENDING)
                .map(OneTimePaymentJpaEntity::toDomain);
    }
}
