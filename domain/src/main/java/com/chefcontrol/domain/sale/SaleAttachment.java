package com.chefcontrol.domain.sale;

import java.time.Instant;
import java.util.UUID;

// ponytail: copia de la de compras (tanda 7). Si aparece un tercer dueño de adjuntos, generalizar en una sola.
/** El ticket Z o reporte de caja de una venta. Los bytes van aparte (SaleAttachmentRepository): el listado no los arrastra. */
public record SaleAttachment(UUID id, UUID saleId, UUID restaurantId, String contentType, String filename,
                                 int sizeBytes, Instant createdAt) {}
