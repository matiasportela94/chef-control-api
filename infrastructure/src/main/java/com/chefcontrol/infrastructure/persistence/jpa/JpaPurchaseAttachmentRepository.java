package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.PurchaseAttachmentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface JpaPurchaseAttachmentRepository extends JpaRepository<PurchaseAttachmentJpaEntity, UUID> {

    /** Los metadatos, sin los bytes. */
    @Query("SELECT a.id, a.purchaseId, a.restaurantId, a.contentType, a.filename, a.sizeBytes, a.createdAt "
            + "FROM PurchaseAttachmentJpaEntity a WHERE a.purchaseId = :purchaseId ORDER BY a.createdAt")
    List<Object[]> findMetadataByPurchaseId(@Param("purchaseId") UUID purchaseId);

    @Query("SELECT a.bytes FROM PurchaseAttachmentJpaEntity a WHERE a.id = :id")
    List<byte[]> findBytesById(@Param("id") UUID id);

    @Query("SELECT a.purchaseId, COUNT(a) FROM PurchaseAttachmentJpaEntity a WHERE a.purchaseId IN :ids GROUP BY a.purchaseId")
    List<Object[]> countByPurchaseIds(@Param("ids") Collection<UUID> ids);

    @Modifying
    @Query("UPDATE PurchaseAttachmentJpaEntity a SET a.purchaseId = :to WHERE a.purchaseId = :from")
    int moveAll(@Param("from") UUID from, @Param("to") UUID to);
}
