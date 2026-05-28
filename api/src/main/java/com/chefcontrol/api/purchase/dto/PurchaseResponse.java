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
        Instant createdAt
) {
    public static PurchaseResponse from(Purchase purchase) {
        return new PurchaseResponse(
                purchase.getId(),
                purchase.getSupplierId(),
                purchase.getSupplierName(),
                purchase.getTotal(),
                purchase.getNotes(),
                purchase.getPurchasedAt(),
                purchase.getCreatedAt());
    }
}
