package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.purchase.PurchaseItem;
import com.chefcontrol.domain.repository.PurchaseItemRepository;
import com.chefcontrol.infrastructure.persistence.entity.PurchaseItemJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaPurchaseItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PurchaseItemRepositoryAdapter implements PurchaseItemRepository {

    private final JpaPurchaseItemRepository jpa;

    @Override
    public List<PurchaseItem> findByPurchaseIdOrderByCreatedAtAsc(UUID purchaseId) {
        return jpa.findByPurchaseIdOrderByCreatedAtAsc(purchaseId).stream()
                .map(PurchaseItemJpaEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public java.util.Optional<PurchaseItem> findById(UUID id) {
        return jpa.findWithRelationshipsById(id).map(PurchaseItemJpaEntity::toDomain);
    }

    @Override
    public java.util.Optional<LastPurchaseLine> findLastActiveLineOfProduct(UUID productId, UUID restaurantId) {
        return jpa.findActiveLinesOfProduct(productId, restaurantId, org.springframework.data.domain.PageRequest.of(0, 1))
                .stream().findFirst()
                .map(r -> new LastPurchaseLine((java.math.BigDecimal) r[0], (UUID) r[1], (java.math.BigDecimal) r[2],
                        (java.time.Instant) r[3], (Boolean) r[4]));
    }

    @Override
    public List<PurchaseItem> findByPurchaseIdIn(java.util.Collection<UUID> purchaseIds) {
        if (purchaseIds.isEmpty()) return List.of();
        return jpa.findByPurchaseIdIn(purchaseIds).stream().map(PurchaseItemJpaEntity::toDomain).toList();
    }

    @Override
    public PurchaseItem save(PurchaseItem item) {
        PurchaseItemJpaEntity saved = jpa.save(PurchaseItemJpaEntity.from(item));
        return jpa.findWithRelationshipsById(saved.getId())
                .map(PurchaseItemJpaEntity::toDomain)
                .orElse(saved.toDomain());
    }
}
