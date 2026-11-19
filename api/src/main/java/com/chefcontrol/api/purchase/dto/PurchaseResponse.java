package com.chefcontrol.api.purchase.dto;

import com.chefcontrol.domain.purchase.Purchase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PurchaseResponse(
        UUID id,
        UUID supplierId,
        String supplierName,
        BigDecimal total,
        String notes,
        Instant purchasedAt,
        Instant createdAt,
        /** Los tres null en una compra anterior al IVA. */
        Boolean pricesIncludeIva,
        BigDecimal netTotal,
        BigDecimal ivaTotal,
        com.chefcontrol.domain.purchase.InvoiceType invoiceType,
        String invoiceNumber,
        /** Cuántas facturas o remitos tiene adjuntos (GET /purchases/{id}/attachments). */
        int attachmentCount
) {
    public static PurchaseResponse from(Purchase purchase, int attachmentCount) {
        return new PurchaseResponse(
                purchase.getId(),
                purchase.getSupplierId(),
                purchase.getSupplierName(),
                purchase.getTotal(),
                purchase.getNotes(),
                purchase.getPurchasedAt(),
                purchase.getCreatedAt(),
                purchase.getPricesIncludeIva(),
                purchase.getNetTotal(),
                purchase.getIvaTotal(),
                purchase.getInvoiceType(),
                purchase.getInvoiceNumber(),
                attachmentCount);
    }
}
