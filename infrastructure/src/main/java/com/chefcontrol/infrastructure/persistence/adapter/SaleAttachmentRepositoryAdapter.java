package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.sale.SaleAttachment;
import com.chefcontrol.domain.repository.SaleAttachmentRepository;
import com.chefcontrol.infrastructure.persistence.entity.SaleAttachmentJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaSaleAttachmentRepository;
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
// ponytail: copia de la de compras (tanda 7). Si aparece un tercer dueño de adjuntos, generalizar en una sola.
public class SaleAttachmentRepositoryAdapter implements SaleAttachmentRepository {

    private final JpaSaleAttachmentRepository jpa;

    @Override
    public SaleAttachment save(SaleAttachment a, byte[] bytes) {
        SaleAttachmentJpaEntity e = new SaleAttachmentJpaEntity();
        e.setSaleId(a.saleId());
        e.setRestaurantId(a.restaurantId());
        e.setContentType(a.contentType());
        e.setFilename(a.filename());
        e.setSizeBytes(a.sizeBytes());
        e.setBytes(bytes);
        e.setCreatedAt(a.createdAt());
        SaleAttachmentJpaEntity saved = jpa.saveAndFlush(e);
        return new SaleAttachment(saved.getId(), a.saleId(), a.restaurantId(), a.contentType(), a.filename(),
                a.sizeBytes(), a.createdAt());
    }

    @Override
    public List<SaleAttachment> findBySaleId(UUID saleId) {
        return jpa.findMetadataBySaleId(saleId).stream().map(SaleAttachmentRepositoryAdapter::metadata).toList();
    }

    @Override
    public Optional<SaleAttachment> findByIdAndSaleId(UUID id, UUID saleId) {
        return findBySaleId(saleId).stream().filter(a -> a.id().equals(id)).findFirst();
    }

    @Override
    public Optional<byte[]> findBytes(UUID id) {
        return jpa.findBytesById(id).stream().findFirst();
    }

    @Override
    public void delete(UUID id) {
        jpa.deleteById(id);
    }



    private static SaleAttachment metadata(Object[] r) {
        return new SaleAttachment((UUID) r[0], (UUID) r[1], (UUID) r[2], (String) r[3], (String) r[4],
                ((Number) r[5]).intValue(), (Instant) r[6]);
    }
}
