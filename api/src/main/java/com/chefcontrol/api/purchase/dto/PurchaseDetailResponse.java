package com.chefcontrol.api.purchase.dto;

import com.chefcontrol.domain.purchase.Purchase;
import com.chefcontrol.domain.purchase.PurchaseItem;

import java.math.BigDecimal;
import java.time.Instant;
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
                items.stream().map(ItemDetail::from).toList());
    }

    public record ItemDetail(
            UUID id,
            UUID productId,
            String productName,
            String productSku,
            BigDecimal quantity,
            BigDecimal quantityRemaining,
            UUID unitId,
            String unitName,
            String unitAbbreviation,
            BigDecimal pricePerUnit,
            BigDecimal subtotal
    ) {
        static ItemDetail from(PurchaseItem item) {
            return new ItemDetail(
                    item.getId(),
                    item.getProductId(),
                    item.getProductName(),
                    item.getProductSku(),
                    item.getQuantity(),
                    item.getQuantityRemaining(),
                    item.getUnitId(),
                    item.getUnitName(),
                    item.getUnitAbbreviation(),
                    item.getPricePerUnit(),
                    item.getQuantity().multiply(item.getPricePerUnit()));
        }
    }
}
