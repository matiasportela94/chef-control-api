package com.chefcontrol.api.purchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreatePurchaseRequest(
        UUID supplierId,
        @Size(max = 1000) String notes,
        Instant purchasedAt,
        /** Si los precios traen el IVA adentro (Factura B). Null = el default del restaurante: false si es
         *  responsable inscripto, true si es monotributo o no tiene condición cargada. */
        Boolean pricesIncludeIva,
        @NotEmpty @Valid List<PurchaseItemRequest> items,
        com.chefcontrol.domain.purchase.InvoiceType invoiceType,
        @Size(max = 30) String invoiceNumber,
        /** "Corregir": la compra revertida cuya factura pasa a esta (CONFLICT ATTACHMENTS_NOT_MOVABLE si no lo está). */
        UUID attachmentsFromPurchaseId
) {}
