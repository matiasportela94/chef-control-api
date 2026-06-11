package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.purchase.PurchaseItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseItemRepository {

    List<PurchaseItem> findByPurchaseIdOrderByCreatedAtAsc(UUID purchaseId);

    Optional<PurchaseItem> findById(UUID id);

    PurchaseItem save(PurchaseItem item);
}
