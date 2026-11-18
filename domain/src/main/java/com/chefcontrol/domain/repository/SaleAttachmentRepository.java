package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.sale.SaleAttachment;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// ponytail: copia de la de compras (tanda 7). Si aparece un tercer dueño de adjuntos, generalizar en una sola.
public interface SaleAttachmentRepository {

    SaleAttachment save(SaleAttachment attachment, byte[] bytes);

    List<SaleAttachment> findBySaleId(UUID saleId);

    Optional<SaleAttachment> findByIdAndSaleId(UUID id, UUID saleId);

    Optional<byte[]> findBytes(UUID id);

    void delete(UUID id);

}
