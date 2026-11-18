package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.purchase.PurchaseItem;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseItemRepository {

    List<PurchaseItem> findByPurchaseIdOrderByCreatedAtAsc(UUID purchaseId);

    Optional<PurchaseItem> findById(UUID id);

    PurchaseItem save(PurchaseItem item);

    List<PurchaseItem> findByPurchaseIdIn(Collection<UUID> purchaseIds);

    /** La línea de la última compra no revertida de ese insumo (por fecha de la compra), tal como se cargó. */
    Optional<LastPurchaseLine> findLastActiveLineOfProduct(UUID productId, UUID restaurantId);

    /** @param pricesIncludeIva null en una compra vieja, de antes del IVA */
    record LastPurchaseLine(java.math.BigDecimal quantity, UUID unitId, java.math.BigDecimal pricePerUnit,
                            java.time.Instant purchasedAt, Boolean pricesIncludeIva) {}
}
