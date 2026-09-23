package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.ProductYieldHistoryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaProductYieldHistoryRepository extends JpaRepository<ProductYieldHistoryJpaEntity, UUID> {

    /** El tramo vigente en {@code at}: el último que arrancó antes o en ese momento. */
    Optional<ProductYieldHistoryJpaEntity> findFirstByProductIdAndValidFromLessThanEqualOrderByValidFromDesc(
            UUID productId, Instant at);

    List<ProductYieldHistoryJpaEntity> findByProductIdOrderByValidFromAsc(UUID productId);
}
