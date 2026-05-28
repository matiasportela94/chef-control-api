package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.purchase.PurchaseItem;

import java.util.List;
import java.util.UUID;

public interface PurchaseItemRepository {

    List<PurchaseItem> findByPurchaseIdOrderByCreatedAtAsc(UUID purchaseId);

    PurchaseItem save(PurchaseItem item);
}
