package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.purchase.PurchaseAttachment;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseAttachmentRepository {

    PurchaseAttachment save(PurchaseAttachment attachment, byte[] bytes);

    List<PurchaseAttachment> findByPurchaseId(UUID purchaseId);

    Optional<PurchaseAttachment> findByIdAndPurchaseId(UUID id, UUID purchaseId);

    Optional<byte[]> findBytes(UUID id);

    void delete(UUID id);

    /** Cuántos adjuntos tiene cada una de esas compras (las que no tienen, no aparecen). */
    Map<UUID, Integer> countByPurchaseIds(Collection<UUID> purchaseIds);

    /** "Corregir" (revertir y recrear): la factura pasa a la compra nueva. */
    void moveAll(UUID fromPurchaseId, UUID toPurchaseId);
}
