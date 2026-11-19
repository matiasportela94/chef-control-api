package com.chefcontrol.infrastructure.persistence.entity;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Sin @Lob: con PostgreSQL, @Lob sobre byte[] espera una columna oid y no bytea (pasó con las fotos de los platos). */
@Entity
@Table(name = "purchase_attachments")
@Getter @Setter @NoArgsConstructor
public class PurchaseAttachmentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "purchase_id", nullable = false)
    private UUID purchaseId;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    private String filename;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    private byte[] bytes;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
