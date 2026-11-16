package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.domain.payments.OneTimePaymentStatus;
import com.chefcontrol.infrastructure.persistence.entity.OneTimePaymentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaOneTimePaymentRepository extends JpaRepository<OneTimePaymentJpaEntity, UUID> {

    Optional<OneTimePaymentJpaEntity> findFirstByAccountIdAndPurposeAndStatusOrderByCreatedAtDesc(
            UUID accountId, String purpose, OneTimePaymentStatus status);
}
