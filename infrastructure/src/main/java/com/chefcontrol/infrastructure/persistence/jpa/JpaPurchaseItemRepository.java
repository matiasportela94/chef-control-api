package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.PurchaseItemJpaEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaPurchaseItemRepository extends JpaRepository<PurchaseItemJpaEntity, UUID> {

    @EntityGraph(attributePaths = {"product", "unit"})
    List<PurchaseItemJpaEntity> findByPurchaseIdOrderByCreatedAtAsc(UUID purchaseId);

    @EntityGraph(attributePaths = {"product", "unit"})
    java.util.Optional<PurchaseItemJpaEntity> findWithRelationshipsById(UUID id);
}
