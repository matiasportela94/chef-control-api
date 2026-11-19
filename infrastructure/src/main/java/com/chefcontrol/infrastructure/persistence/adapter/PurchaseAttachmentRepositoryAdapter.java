package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.purchase.PurchaseAttachment;
import com.chefcontrol.domain.repository.PurchaseAttachmentRepository;
import com.chefcontrol.infrastructure.persistence.entity.PurchaseAttachmentJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaPurchaseAttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PurchaseAttachmentRepositoryAdapter implements PurchaseAttachmentRepository {

    private final JpaPurchaseAttachmentRepository jpa;

    @Override
    public PurchaseAttachment save(PurchaseAttachment a, byte[] bytes) {
        PurchaseAttachmentJpaEntity e = new PurchaseAttachmentJpaEntity();
        e.setPurchaseId(a.purchaseId());
        e.setRestaurantId(a.restaurantId());
        e.setContentType(a.contentType());
        e.setFilename(a.filename());
        e.setSizeBytes(a.sizeBytes());
        e.setBytes(bytes);
        e.setCreatedAt(a.createdAt());
        PurchaseAttachmentJpaEntity saved = jpa.saveAndFlush(e);
        return new PurchaseAttachment(saved.getId(), a.purchaseId(), a.restaurantId(), a.contentType(), a.filename(),
                a.sizeBytes(), a.createdAt());
    }

    @Override
    public List<PurchaseAttachment> findByPurchaseId(UUID purchaseId) {
        return jpa.findMetadataByPurchaseId(purchaseId).stream().map(PurchaseAttachmentRepositoryAdapter::metadata).toList();
    }

    @Override
    public Optional<PurchaseAttachment> findByIdAndPurchaseId(UUID id, UUID purchaseId) {
        return findByPurchaseId(purchaseId).stream().filter(a -> a.id().equals(id)).findFirst();
    }

    @Override
    public Optional<byte[]> findBytes(UUID id) {
        return jpa.findBytesById(id).stream().findFirst();
    }

    @Override
    public void delete(UUID id) {
        jpa.deleteById(id);
    }

    @Override
    public Map<UUID, Integer> countByPurchaseIds(Collection<UUID> purchaseIds) {
        Map<UUID, Integer> counts = new HashMap<>();
        jpa.countByPurchaseIds(purchaseIds).forEach(r -> counts.put((UUID) r[0], ((Number) r[1]).intValue()));
        return counts;
    }

    @Override
    public void moveAll(UUID fromPurchaseId, UUID toPurchaseId) {
        jpa.moveAll(fromPurchaseId, toPurchaseId);
    }

    private static PurchaseAttachment metadata(Object[] r) {
        return new PurchaseAttachment((UUID) r[0], (UUID) r[1], (UUID) r[2], (String) r[3], (String) r[4],
                ((Number) r[5]).intValue(), (Instant) r[6]);
    }
}
