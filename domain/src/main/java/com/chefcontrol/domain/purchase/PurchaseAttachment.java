package com.chefcontrol.domain.purchase;

import java.time.Instant;
import java.util.UUID;

/** La factura o el remito de una compra. Los bytes van aparte (PurchaseAttachmentRepository): el listado no los arrastra. */
public record PurchaseAttachment(UUID id, UUID purchaseId, UUID restaurantId, String contentType, String filename,
                                 int sizeBytes, Instant createdAt) {}
