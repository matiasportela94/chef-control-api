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
    public PurchaseItem save(PurchaseItem item) {
        PurchaseItemJpaEntity saved = jpa.save(PurchaseItemJpaEntity.from(item));
        return jpa.findWithRelationshipsById(saved.getId())
                .map(PurchaseItemJpaEntity::toDomain)
                .orElse(saved.toDomain());
    }
}
