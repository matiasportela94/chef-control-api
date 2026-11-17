package com.chefcontrol.api.purchase.dto;

import com.chefcontrol.domain.purchase.Purchase;
import com.chefcontrol.domain.purchase.PurchaseItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseDetailResponse(
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
        List<ItemDetail> items
) {
    public static PurchaseDetailResponse from(Purchase purchase, List<PurchaseItem> items) {
        return new PurchaseDetailResponse(
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
                items.stream().map(ItemDetail::from).toList());
    }

    public record ItemDetail(
            UUID id,
            UUID productId,
            String productName,
            String productSku,
            BigDecimal quantity,
            BigDecimal quantityRemaining,
            LocalDate expirationDate,
            UUID unitId,
            String unitName,
            String unitAbbreviation,
            /** Como se cargó: con o sin IVA según {@code pricesIncludeIva} de la compra. */
            BigDecimal pricePerUnit,
            /** Cantidad × precio cargado, sin redondear. */
            BigDecimal subtotal,
            /** Los tres null en una línea anterior al IVA. */
            BigDecimal ivaPercentage,
            BigDecimal netAmount,
            BigDecimal ivaAmount
    ) {
        static ItemDetail from(PurchaseItem item) {
            return new ItemDetail(
                    item.getId(),
                    item.getProductId(),
                    item.getProductName(),
                    item.getProductSku(),
                    item.getQuantity(),
                    item.getQuantityRemaining(),
                    item.getExpirationDate(),
                    item.getUnitId(),
                    item.getUnitName(),
                    item.getUnitAbbreviation(),
                    item.getPricePerUnit(),
                    item.getQuantity().multiply(item.getPricePerUnit()),
                    item.getIvaPercentage(),
                    item.getNetAmount(),
                    item.getIvaAmount());
        }
    }
}
