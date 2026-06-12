package com.chefcontrol.api.sale.dto;

import com.chefcontrol.domain.sale.Sale;
import com.chefcontrol.domain.sale.SaleItem;
import com.chefcontrol.domain.sale.SaleStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SaleResponse(
        UUID id,
        BigDecimal totalAmount,
        String source,
        String notes,
        Instant soldAt,
        Instant createdAt,
        Integer itemCount,
        SaleStatus status,
        List<SaleItemResponse> items
) {
    public record SaleItemResponse(
            UUID id,
            UUID menuItemId,
            String menuItemName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {
        public static SaleItemResponse from(SaleItem si) {
            return new SaleItemResponse(
                    si.getId(),
                    si.getMenuItemId(),
                    si.getMenuItemName(),
                    si.getQuantity(),
                    si.getUnitPrice(),
                    si.getUnitPrice().multiply(BigDecimal.valueOf(si.getQuantity()))
            );
        }
    }

    public static SaleResponse summary(Sale sale, int itemCount) {
        return new SaleResponse(sale.getId(), sale.getTotalAmount(), sale.getSource(),
                sale.getNotes(), sale.getSoldAt(), sale.getCreatedAt(), itemCount, sale.getStatus(), null);
    }

    public static SaleResponse from(Sale sale, List<SaleItem> items) {
        return new SaleResponse(
                sale.getId(),
                sale.getTotalAmount(),
                sale.getSource(),
                sale.getNotes(),
                sale.getSoldAt(),
                sale.getCreatedAt(),
                items.size(),
                sale.getStatus(),
                items.stream().map(SaleItemResponse::from).toList()
        );
    }
}
