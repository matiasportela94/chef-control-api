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
    List<PurchaseItemJpaEntity> findByPurchaseIdIn(java.util.Collection<UUID> purchaseIds);

    @EntityGraph(attributePaths = {"product", "unit"})
    java.util.Optional<PurchaseItemJpaEntity> findWithRelationshipsById(UUID id);

    @org.springframework.data.jpa.repository.Query("""
            SELECT i.quantity, i.unitId, i.pricePerUnit, p.purchasedAt, p.pricesIncludeIva
            FROM PurchaseItemJpaEntity i, PurchaseJpaEntity p
            WHERE p.id = i.purchaseId AND i.productId = :productId AND p.restaurantId = :restaurantId
              AND p.status = 'ACTIVE'
            ORDER BY p.purchasedAt DESC, i.createdAt DESC
            """)
    List<Object[]> findActiveLinesOfProduct(@org.springframework.data.repository.query.Param("productId") UUID productId,
                                            @org.springframework.data.repository.query.Param("restaurantId") UUID restaurantId,
                                            org.springframework.data.domain.Pageable pageable);
}
