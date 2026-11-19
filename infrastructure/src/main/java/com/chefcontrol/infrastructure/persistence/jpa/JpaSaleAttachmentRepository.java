package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.SaleAttachmentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

// ponytail: copia de la de compras (tanda 7). Si aparece un tercer dueño de adjuntos, generalizar en una sola.
public interface JpaSaleAttachmentRepository extends JpaRepository<SaleAttachmentJpaEntity, UUID> {

    /** Los metadatos, sin los bytes. */
    @Query("SELECT a.id, a.saleId, a.restaurantId, a.contentType, a.filename, a.sizeBytes, a.createdAt "
            + "FROM SaleAttachmentJpaEntity a WHERE a.saleId = :saleId ORDER BY a.createdAt")
    List<Object[]> findMetadataBySaleId(@Param("saleId") UUID saleId);

    @Query("SELECT a.bytes FROM SaleAttachmentJpaEntity a WHERE a.id = :id")
    List<byte[]> findBytesById(@Param("id") UUID id);


}
